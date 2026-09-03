package com.ovi.handoff.mobile.feature.pairing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ovi.handoff.mobile.core.theme.*
import com.ovi.handoff.mobile.feature.pairing.ui.components.QrScanner
import com.ovi.handoff.mobile.feature.pairing.viewmodel.PairingEvent
import com.ovi.handoff.mobile.feature.pairing.viewmodel.PairingViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

public enum class PairingMode {
    QR_SCAN,
    MANUAL_ENTRY
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
public fun PairingScreen(
    viewModel: PairingViewModel = koinViewModel(),
    onPairingSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var selectedMode by remember { mutableStateOf(PairingMode.QR_SCAN) }
    var manualCode by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is PairingEvent.PairingSuccess) {
                onPairingSuccess()
            }
        }
    }

    HandoffTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBg
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isPairing -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = AntigravityViolet)
                            Text(
                                text = "Pairing with Antigravity Desktop...",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    uiState.error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Error: ${uiState.error}",
                                color = RiskCritical,
                                fontSize = 15.sp
                            )
                            Button(
                                onClick = { viewModel.onResumeScanning() },
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityViolet)
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AntigravityViolet))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pair with Antigravity",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Mode Selector Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedMode == PairingMode.QR_SCAN) AntigravityViolet else Color.Transparent)
                                        .clickable { selectedMode = PairingMode.QR_SCAN }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Scan QR Code",
                                        color = if (selectedMode == PairingMode.QR_SCAN) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedMode == PairingMode.MANUAL_ENTRY) AntigravityViolet else Color.Transparent)
                                        .clickable { selectedMode = PairingMode.MANUAL_ENTRY }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Manual Code",
                                        color = if (selectedMode == PairingMode.MANUAL_ENTRY) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Body Content
                            if (selectedMode == PairingMode.QR_SCAN) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cameraPermissionState.status.isGranted) {
                                        QrScanner(
                                            onQrCodeScanned = viewModel::onQrCodeScanned,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Camera access required to scan the desktop pairing QR code.",
                                                color = TextSecondary,
                                                fontSize = 13.sp
                                            )
                                            Button(
                                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityViolet)
                                            ) {
                                                Text("Enable Camera")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Enter Pair ID or paste JSON payload:",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )

                                    OutlinedTextField(
                                        value = manualCode,
                                        onValueChange = { manualCode = it },
                                        placeholder = { Text("e.g. test-pixel-99", color = TextMuted, fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = DarkBg,
                                            unfocusedContainerColor = DarkBg,
                                            focusedBorderColor = AntigravityViolet,
                                            unfocusedBorderColor = DarkBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    // Clipboard Paste Button
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = clipboardManager.getText()?.text
                                            if (!clipboard.isNullOrBlank()) {
                                                manualCode = clipboard
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                                    ) {
                                        Text("Paste from Clipboard", fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = {
                                            if (manualCode.isNotBlank()) {
                                                viewModel.onQrCodeScanned(manualCode.trim())
                                            }
                                        },
                                        enabled = manualCode.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AntigravityViolet,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Connect & Pair", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

