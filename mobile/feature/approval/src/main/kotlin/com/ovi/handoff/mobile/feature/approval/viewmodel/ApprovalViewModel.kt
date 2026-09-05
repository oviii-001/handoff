package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.repository.ConnectedSession
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.settings.HandoffSettings
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.ClearRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ExpireOverdueRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SubmitDecisionUseCase
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import com.ovi.handoff.mobile.feature.approval.ui.model.AuditEntryUiModel
import com.ovi.handoff.mobile.feature.approval.ui.model.AuditOutcome
import com.ovi.handoff.mobile.feature.approval.ui.model.ConnectedAgentUiModel
import com.ovi.handoff.mobile.feature.approval.ui.model.PermissionRequestUiModel
import com.ovi.handoff.mobile.feature.approval.ui.model.toAuditEntry
import com.ovi.handoff.mobile.feature.approval.ui.model.toUiModel
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class ApprovalTab {
    HOME,
    AUDIT,
    SETTINGS
}

/**
 * State for the approval surfaces.
 *
 * Everything the UI reads is a plain, precomputed property. The previous version exposed
 * `connectedAgent`, `activeProjectOrWorkspace` and `recentActivity` as `get()` accessors that walked
 * and re-allocated lists on every single read, and every read happens inside composition.
 */
public data class ApprovalUiState(
    /** All requests awaiting a decision, most urgent first. */
    val pendingRequests: ImmutableList<PermissionRequestUiModel> = persistentListOf(),
    val activeRequestIndex: Int = 0,
    val connectionState: ConnectionState = ConnectionState.OFFLINE,
    val isSendingDecision: Boolean = false,
    val selectedTab: ApprovalTab = ApprovalTab.HOME,
    val auditEntries: ImmutableList<AuditEntryUiModel> = persistentListOf(),
    val filteredAuditEntries: ImmutableList<AuditEntryUiModel> = persistentListOf(),
    val recentActivity: ImmutableList<AuditEntryUiModel> = persistentListOf(),
    val searchQuery: String = "",
    val filterRisk: String? = null,
    val selectedAgentFilter: String? = null,
    val isAbortingSession: Boolean = false,
    val pairId: String? = null,
    val isPaired: Boolean = false,
    val isPairing: Boolean = false,
    val pairingError: String? = null,
    val error: String? = null,
    val notificationMessage: String? = null,
    val settings: HandoffSettings = HandoffSettings(),
    val connectedAgent: ConnectedAgentUiModel? = null,
    val activeWorkspaceLabel: String? = null,
    val activeWorkspacePath: String? = null,
    val decidedCount: Int = 0
) {
    /** The request currently on screen, or null when the queue is empty. */
    val displayedRequest: PermissionRequestUiModel?
        get() = pendingRequests.getOrNull(activeRequestIndex)

    val pendingCount: Int get() = pendingRequests.size
}

public class ApprovalViewModel(
    private val initialPairId: String? = null,
    private val relayRepository: RelayRepository,
    private val observeRequestsUseCase: ObserveRequestsUseCase,
    private val submitDecisionUseCase: SubmitDecisionUseCase,
    private val getRequestHistoryUseCase: GetRequestHistoryUseCase,
    private val clearRequestHistoryUseCase: ClearRequestHistoryUseCase,
    private val expireOverdueRequestsUseCase: ExpireOverdueRequestsUseCase,
    private val abortSessionUseCase: AbortSessionUseCase,
    private val pairDeviceUseCase: PairDeviceUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val pairingRepository: PairingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ApprovalUiState(
            pairId = initialPairId?.takeIf { it.isNotBlank() },
            isPaired = !initialPairId.isNullOrBlank()
        )
    )
    public val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /** Raw pending requests, kept so the domain object is available when signing a decision. */
    private var pendingDomainRequests: List<PermissionRequest> = emptyList()

    init {
        resolvePairing()
        observeHistory()
        observeConnectedSession()
        observeConnectionState()
        observeSettings()
        sweepExpired()
    }

    // -----------------------------------------------------------------------------------------
    // Wiring
    // -----------------------------------------------------------------------------------------

    private fun resolvePairing() {
        viewModelScope.launch {
            val activeId = initialPairId?.takeIf { it.isNotBlank() } ?: pairingRepository.getPairId()
            if (activeId.isNullOrBlank()) {
                _uiState.update { it.copy(pairId = null, isPaired = false) }
                return@launch
            }
            _uiState.update { it.copy(pairId = activeId, isPaired = true) }
            startObservingRequests(activeId)
        }
    }

    private fun startObservingRequests(activePairId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeRequestsUseCase(activePairId).collect { requests ->
                pendingDomainRequests = requests
                // Most urgent first: highest risk, then oldest, so a critical request cannot sit
                // behind a queue of low-risk ones.
                val ordered = requests
                    .map { it.toUiModel() }
                    .sortedWith(compareByDescending<PermissionRequestUiModel> { it.riskWeight }.thenBy { it.createdAtEpochMs })
                    .toImmutableList()

                _uiState.update { state ->
                    state.copy(
                        pendingRequests = ordered,
                        activeRequestIndex = state.activeRequestIndex.coerceIn(0, maxOf(0, ordered.lastIndex))
                    )
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeHistory() {
        viewModelScope.launch {
            // Filtering lives here, not in the composable. The audit screen used to re-filter the
            // entire history on every recomposition, so each keystroke walked the whole table.
            val entries = getRequestHistoryUseCase()
                .map { records -> records.map { it.toAuditEntry() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

            val query = _uiState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MS)

            val filters = _uiState
                .map { it.filterRisk to it.selectedAgentFilter }
                .distinctUntilChanged()

            combine(entries, query, filters) { all, searchQuery, filterPair ->
                val (risk, agentId) = filterPair
                all to all.filter { entry -> matches(entry, searchQuery, risk, agentId) }
            }.collect { (all, filtered) ->
                _uiState.update { state ->
                    state.copy(
                        auditEntries = all.toImmutableList(),
                        filteredAuditEntries = filtered.toImmutableList(),
                        recentActivity = all.take(RECENT_ACTIVITY_COUNT).toImmutableList(),
                        decidedCount = all.count { it.outcome != AuditOutcome.PENDING },
                        // Falls back to the most recent request's agent, so the home screen still
                        // names the IDE after a restart when no session announcement has arrived yet.
                        connectedAgent = state.connectedAgent ?: all.firstOrNull()?.let { entry ->
                            ConnectedAgentUiModel(
                                id = entry.request.agentId,
                                name = entry.request.agentName,
                                version = entry.request.agentVersion
                            )
                        }
                    )
                }
            }
        }
    }

    private fun matches(
        entry: AuditEntryUiModel,
        searchQuery: String,
        risk: String?,
        agentId: String?
    ): Boolean {
        val request = entry.request
        val matchesQuery = searchQuery.isBlank() ||
            request.command?.contains(searchQuery, ignoreCase = true) == true ||
            request.description?.contains(searchQuery, ignoreCase = true) == true ||
            request.agentName.contains(searchQuery, ignoreCase = true) ||
            request.permissionType.contains(searchQuery, ignoreCase = true) ||
            request.workspaceLabel?.contains(searchQuery, ignoreCase = true) == true

        val matchesRisk = risk == null || request.riskLevel.equals(risk, ignoreCase = true)
        val matchesAgent = agentId == null || request.agentId.equals(agentId, ignoreCase = true)
        return matchesQuery && matchesRisk && matchesAgent
    }

    private fun observeConnectedSession() {
        viewModelScope.launch {
            pairingRepository.observeConnectedSession().collect { session: ConnectedSession? ->
                _uiState.update { state ->
                    state.copy(
                        connectedAgent = session?.let {
                            ConnectedAgentUiModel(id = it.ideName.lowercase(), name = it.ideName)
                        } ?: state.connectedAgent,
                        activeWorkspacePath = session?.workspaceName ?: state.activeWorkspacePath,
                        activeWorkspaceLabel = session?.workspaceName?.let(::folderName)
                            ?: state.activeWorkspaceLabel
                    )
                }
            }
        }
    }

    private fun folderName(path: String): String =
        path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

    private fun observeConnectionState() {
        viewModelScope.launch {
            relayRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.observe().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    /** Clears out anything whose deadline passed while the app was away. */
    public fun sweepExpired() {
        viewModelScope.launch { expireOverdueRequestsUseCase() }
    }

    // -----------------------------------------------------------------------------------------
    // Pairing
    // -----------------------------------------------------------------------------------------

    public fun pairWithCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(pairingError = "Enter or scan a pairing code first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, pairingError = null) }
            pairDeviceUseCase(trimmed).fold(
                onSuccess = {
                    val activeId = pairingRepository.getPairId() ?: trimmed
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            isPaired = true,
                            pairId = activeId,
                            notificationMessage = "Paired. Waiting for your agent."
                        )
                    }
                    startObservingRequests(activeId)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingError = error.message ?: "Could not pair with that code."
                        )
                    }
                }
            )
        }
    }

    public fun unpair() {
        viewModelScope.launch {
            observeJob?.cancel()
            unpairDeviceUseCase()
            _uiState.update {
                ApprovalUiState(
                    selectedTab = ApprovalTab.HOME,
                    notificationMessage = "Unpaired. Your signing key was retired."
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Navigation and filters
    // -----------------------------------------------------------------------------------------

    public fun switchTab(tab: ApprovalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    public fun showRequestAt(index: Int) {
        _uiState.update { state ->
            state.copy(activeRequestIndex = index.coerceIn(0, maxOf(0, state.pendingRequests.lastIndex)))
        }
    }

    public fun showNextRequest() {
        _uiState.update { state ->
            val next = (state.activeRequestIndex + 1).coerceAtMost(maxOf(0, state.pendingRequests.lastIndex))
            state.copy(activeRequestIndex = next)
        }
    }

    public fun showPreviousRequest() {
        _uiState.update { state ->
            val prev = (state.activeRequestIndex - 1).coerceAtLeast(0)
            state.copy(activeRequestIndex = prev)
        }
    }

    public fun blockCurrentRequest(reason: String) {
        onReject(reason)
    }

    public fun setAgentFilter(agentId: String?) {
        _uiState.update { it.copy(selectedAgentFilter = agentId) }
    }

    public fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    public fun setFilterRisk(risk: String?) {
        _uiState.update { it.copy(filterRisk = risk) }
    }

    // -----------------------------------------------------------------------------------------
    // Decisions
    // -----------------------------------------------------------------------------------------

    public fun onApprove() {
        submit(DecisionType.APPROVE_ONCE)
    }

    public fun onReject(feedback: String? = null) {
        submit(DecisionType.DENY, feedback = feedback)
    }

    public fun onSubmitQuestion(selectedOptions: List<String>, writeIn: String?) {
        submit(DecisionType.ANSWER_QUESTION, selectedOptions = selectedOptions, feedback = writeIn)
    }

    public fun onProceedPlan() {
        submit(DecisionType.PROCEED_PLAN)
    }

    public fun onRequestPlanChanges(feedback: String) {
        submit(DecisionType.DENY, feedback = feedback)
    }

    private fun submit(
        verdict: String,
        selectedOptions: List<String> = emptyList(),
        feedback: String? = null
    ) {
        val state = _uiState.value
        val displayed = state.displayedRequest ?: return
        val pairId = state.pairId ?: return
        val domainRequest = pendingDomainRequests.firstOrNull { it.id == displayed.id } ?: run {
            _uiState.update { it.copy(error = "That request is no longer available.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingDecision = true, error = null) }

            val result = submitDecisionUseCase(
                pairId = pairId,
                request = domainRequest,
                verdict = verdict,
                feedback = feedback,
                selectedOptions = selectedOptions
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSendingDecision = false,
                            // Show the next queued request rather than an empty screen.
                            activeRequestIndex = 0,
                            notificationMessage = confirmationFor(verdict)
                        )
                    }
                },
                onFailure = { error ->
                    // The request stays in the queue: the decision was not delivered, so telling the
                    // user it was recorded would be a lie they cannot recover from.
                    _uiState.update {
                        it.copy(
                            isSendingDecision = false,
                            error = error.message ?: "Could not send that decision."
                        )
                    }
                }
            )
            sweepExpired()
        }
    }

    private fun confirmationFor(verdict: String): String = when (verdict) {
        DecisionType.APPROVE_ONCE, DecisionType.APPROVE_ALWAYS, DecisionType.APPROVE -> "Approved"
        DecisionType.DENY -> "Denied"
        DecisionType.ANSWER_QUESTION -> "Answer sent"
        DecisionType.PROCEED_PLAN -> "Plan approved"
        else -> "Decision sent"
    }

    public fun onEmergencyHalt() {
        val pairId = _uiState.value.pairId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAbortingSession = true, error = null) }
            abortSessionUseCase(pairId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isAbortingSession = false, notificationMessage = "Halt sent to your agent")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAbortingSession = false,
                            error = error.message ?: "Could not reach your desktop to halt it."
                        )
                    }
                }
            )
        }
    }

    public fun clearAuditHistory() {
        viewModelScope.launch {
            clearRequestHistoryUseCase()
                .onSuccess { _uiState.update { it.copy(notificationMessage = "Audit log cleared") } }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Could not clear the audit log.") }
                }
        }
    }

    public fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    public fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    public fun clearPairingError() {
        _uiState.update { it.copy(pairingError = null) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 180L
        const val RECENT_ACTIVITY_COUNT = 3
    }
}
