package com.ovi.handoff.mobile.feature.approval.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ovi.handoff.mobile.feature.approval.ui.ApprovalScreen
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalRoute(val sessionId: String? = null)

fun NavGraphBuilder.approvalScreen(
    onNavigateToPairingQr: () -> Unit = {}
) {
    composable<ApprovalRoute> { backStackEntry ->
        val route: ApprovalRoute = backStackEntry.toRoute()
        ApprovalScreen(
            pairId = route.sessionId,
            onNavigateToPairingQr = onNavigateToPairingQr
        )
    }
}
