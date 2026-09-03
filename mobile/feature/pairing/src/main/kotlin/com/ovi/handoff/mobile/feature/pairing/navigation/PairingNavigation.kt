package com.ovi.handoff.mobile.feature.pairing.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ovi.handoff.mobile.feature.pairing.ui.PairingScreen
import kotlinx.serialization.Serializable

@Serializable
public data object PairingRoute

public fun NavGraphBuilder.pairingScreen(
    onNavigateBack: () -> Unit = {},
    onPairingSuccess: () -> Unit
) {
    composable<PairingRoute> {
        PairingScreen(
            onNavigateBack = onNavigateBack,
            onPairingSuccess = onPairingSuccess
        )
    }
}
