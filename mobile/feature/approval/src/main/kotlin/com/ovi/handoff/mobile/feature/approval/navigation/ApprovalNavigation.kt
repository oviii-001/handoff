package com.ovi.handoff.mobile.feature.approval.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ovi.handoff.mobile.feature.approval.ui.ApprovalScreen
import kotlinx.serialization.Serializable

import com.ovi.handoff.mobile.feature.approval.ui.settings.SettingsScreen

@Serializable
public data class ApprovalRoute(val sessionId: String? = null)

@Serializable
public data class SettingsRoute(val sessionId: String)

public fun NavGraphBuilder.approvalScreen(
    onNavigateToPairingQr: () -> Unit = {},
    onNavigateToSettings: (String) -> Unit = {}
) {
    composable<ApprovalRoute> { backStackEntry ->
        val route: ApprovalRoute = backStackEntry.toRoute()
        ApprovalScreen(
            pairId = route.sessionId,
            onNavigateToPairingQr = onNavigateToPairingQr
        )
    }
}

public fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onUnpaired: () -> Unit
) {
    composable<SettingsRoute> { backStackEntry ->
        val route: SettingsRoute = backStackEntry.toRoute()
        SettingsScreen(
            pairId = route.sessionId,
            onNavigateBack = onNavigateBack,
            onUnpaired = onUnpaired
        )
    }
}
