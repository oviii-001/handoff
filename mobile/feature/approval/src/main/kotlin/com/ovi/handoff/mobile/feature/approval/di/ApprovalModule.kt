package com.ovi.handoff.mobile.feature.approval.di

import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import com.ovi.handoff.mobile.feature.approval.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val approvalModule: Module = module {
    viewModel { parameters ->
        ApprovalViewModel(
            initialPairId = parameters.getOrNull(),
            relayRepository = get(),
            observeRequestsUseCase = get(),
            submitDecisionUseCase = get(),
            getRequestHistoryUseCase = get(),
            clearRequestHistoryUseCase = get(),
            expireOverdueRequestsUseCase = get(),
            abortSessionUseCase = get(),
            pairDeviceUseCase = get(),
            unpairDeviceUseCase = get(),
            pairingRepository = get(),
            settingsRepository = get()
        )
    }

    viewModel { parameters ->
        SettingsViewModel(
            pairId = parameters.getOrNull() ?: "",
            unpairDeviceUseCase = get(),
            settingsRepository = get(),
            pairingRepository = get(),
            relayRepository = get()
        )
    }
}
