package com.ovi.handoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            MaterialTheme {
                val coroutineScope = rememberCoroutineScope()
                var initialPairId by remember { mutableStateOf<String?>(null) }
                var isCheckingPairing by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    initialPairId = pairingRepository.getPairId()
                    isCheckingPairing = false
                }

                if (isCheckingPairing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val navController = rememberNavController()
                    val startDestination: Any = if (!initialPairId.isNullOrBlank()) {
                        ApprovalRoute(sessionId = initialPairId!!)
                    } else {
                        PairingRoute
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        pairingScreen(
                            onPairingSuccess = {
                                coroutineScope.launch {
                                    val pairId = pairingRepository.getPairId() ?: "test-pair"
                                    navController.navigate(ApprovalRoute(sessionId = pairId)) {
                                        popUpTo<PairingRoute> { inclusive = true }
                                    }
                                }
                            }
                        )
                        approvalScreen()
                    }
                }
            }
        }
    }
}