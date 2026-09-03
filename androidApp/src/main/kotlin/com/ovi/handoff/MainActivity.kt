package com.ovi.handoff

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ovi.handoff.mobile.core.theme.HandoffTheme
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.feature.approval.navigation.ApprovalRoute
import com.ovi.handoff.mobile.feature.approval.navigation.approvalScreen
import com.ovi.handoff.mobile.feature.pairing.navigation.PairingRoute
import com.ovi.handoff.mobile.feature.pairing.navigation.pairingScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val pairingRepository: PairingRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            HandoffTheme {
                val coroutineScope = rememberCoroutineScope()
                var initialPairId by remember { mutableStateOf<String?>(null) }
                var isCheckingPairing by remember { mutableStateOf(true) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Permission handled */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    initialPairId = pairingRepository.getPairId()
                    isCheckingPairing = false
                }

                if (isCheckingPairing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = ApprovalRoute(sessionId = initialPairId),
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut(animationSpec = tween(200))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(animationSpec = tween(200))
                        }
                    ) {
                        approvalScreen(
                            onNavigateToPairingQr = {
                                navController.navigate(PairingRoute)
                            }
                        )

                        pairingScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onPairingSuccess = {
                                coroutineScope.launch {
                                    val pairId = pairingRepository.getPairId() ?: "test-pair"
                                    navController.navigate(ApprovalRoute(sessionId = pairId)) {
                                        popUpTo<ApprovalRoute> { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}