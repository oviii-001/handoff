package com.ovi.handoff.mobile.feature.pairing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ovi.handoff.mobile.core.theme.HandoffTheme
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.core.theme.ShapeLarge
import com.ovi.handoff.mobile.core.theme.ShapeMedium
import com.ovi.handoff.mobile.feature.pairing.R
import com.ovi.handoff.mobile.feature.pairing.ui.components.QrScanner
import com.ovi.handoff.mobile.feature.pairing.viewmodel.PairingEvent
import com.ovi.handoff.mobile.feature.pairing.viewmodel.PairingViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

public enum class PairingMode {
    QR_SCAN,
    MANUAL_ENTRY
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
public fun PairingScreen(
    viewModel: PairingViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
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
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.pairing_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.pairing_nav_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
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
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.pairing_in_progress),
                                color = MaterialTheme.colorScheme.onSurface,
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
                                text = stringResource(R.string.pairing_error_prefix, uiState.error ?: ""),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 15.sp
                            )
                            Button(
                                onClick = { viewModel.onResumeScanning() },
                                shape = ShapeFull,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(stringResource(R.string.pairing_try_again))
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.pairing_header_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Mode Selector Tabs (M3 Expressive segmented pill, zero borders)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeFull)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(ShapeFull)
                                        .background(
                                            if (selectedMode == PairingMode.QR_SCAN)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color.Transparent
                                        )
                                        .clickable { selectedMode = PairingMode.QR_SCAN }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.pairing_tab_qr),
                                        color = if (selectedMode == PairingMode.QR_SCAN)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(ShapeFull)
                                        .background(
                                            if (selectedMode == PairingMode.MANUAL_ENTRY)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color.Transparent
                                        )
                                        .clickable { selectedMode = PairingMode.MANUAL_ENTRY }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.pairing_tab_manual),
                                        color = if (selectedMode == PairingMode.MANUAL_ENTRY)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Mode Body
                            if (selectedMode == PairingMode.QR_SCAN) {
                                if (cameraPermissionState.status.isGranted) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(ShapeExtraLarge)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    ) {
                                        QrScanner(
                                            onQrCodeScanned = { qr ->
                                                viewModel.onQrCodeScanned(qr)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(ShapeExtraLarge)
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.camera_permission_needed_title),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.camera_permission_needed_desc),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { cameraPermissionState.launchPermissionRequest() },
                                            shape = ShapeFull,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.btn_grant_permission),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(ShapeExtraLarge)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.manual_code_prompt),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )

                                    OutlinedTextField(
                                        value = manualCode,
                                        onValueChange = { manualCode = it },
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.manual_code_hint),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 13.sp
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        shape = ShapeMedium
                                    )

                                    // Clipboard Paste Button
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = clipboardManager.getText()?.text
                                            if (!clipboard.isNullOrBlank()) {
                                                manualCode = clipboard
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = ShapeFull,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.btn_paste_from_clipboard),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = {
                                            if (manualCode.isNotBlank()) {
                                                viewModel.onQrCodeScanned(manualCode.trim())
                                            }
                                        },
                                        enabled = manualCode.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = ShapeFull,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.btn_connect_and_pair),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
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
