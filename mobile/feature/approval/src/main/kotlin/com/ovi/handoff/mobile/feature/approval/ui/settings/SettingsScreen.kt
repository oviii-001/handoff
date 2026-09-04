package com.ovi.handoff.mobile.feature.approval.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.HandoffTheme
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.RiskLowColor
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.core.theme.ShapeLarge
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pairId: String,
    onNavigateBack: () -> Unit = {},
    onUnpaired: () -> Unit,
    isBottomTab: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel { parametersOf(pairId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showUnpairDialog by remember { mutableStateOf(false) }

    val hasCamera = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    HandoffTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        if (!isBottomTab) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_nav_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. NOTIFICATIONS & ACTIONS
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_notifications_title),
                    subtitle = stringResource(R.string.settings_notifications_subtitle)
                )

                Card(
                    shape = ShapeExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_push_title),
                            subtitle = stringResource(R.string.settings_push_subtitle),
                            icon = Icons.Outlined.Notifications,
                            checked = uiState.pushNotificationsEnabled,
                            onCheckedChange = viewModel::togglePushNotifications
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_direct_title),
                            subtitle = stringResource(R.string.settings_direct_subtitle),
                            icon = Icons.Outlined.NotificationsActive,
                            checked = uiState.directActionsEnabled,
                            onCheckedChange = viewModel::toggleDirectActions
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_vibration_title),
                            subtitle = stringResource(R.string.settings_vibration_subtitle),
                            icon = Icons.Outlined.Vibration,
                            checked = uiState.vibrationEnabled,
                            onCheckedChange = viewModel::toggleVibration
                        )
                    }
                }

                // 2. DEVICE PERMISSIONS STATUS HUB
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_permissions_hub_title),
                    subtitle = stringResource(R.string.settings_permissions_hub_subtitle)
                )

                Card(
                    shape = ShapeExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PermissionStatusRow(
                            title = stringResource(R.string.perm_camera_label),
                            subtitle = stringResource(R.string.perm_camera_desc),
                            icon = Icons.Outlined.CameraAlt,
                            isGranted = hasCamera
                        )

                        PermissionStatusRow(
                            title = stringResource(R.string.perm_notif_label),
                            subtitle = stringResource(R.string.perm_notif_desc),
                            icon = Icons.Outlined.Notifications,
                            isGranted = hasNotification
                        )

                        PermissionStatusRow(
                            title = stringResource(R.string.perm_biometric_label),
                            subtitle = stringResource(R.string.perm_biometric_desc),
                            icon = Icons.Outlined.Fingerprint,
                            isGranted = true,
                            statusLabel = stringResource(R.string.status_available)
                        )
                    }
                }

                // 3. SECURITY & ZERO-TRUST
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_security_title),
                    subtitle = stringResource(R.string.settings_security_subtitle)
                )

                Card(
                    shape = ShapeExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_biometrics_title),
                            subtitle = stringResource(R.string.settings_biometrics_subtitle),
                            icon = Icons.Outlined.Fingerprint,
                            checked = uiState.biometricsRequiredForCritical,
                            onCheckedChange = viewModel::toggleBiometrics
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_signing_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_signing_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(ShapeFull)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_enforced),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. RELAY & SESSION
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_relay_title),
                    subtitle = stringResource(R.string.settings_relay_subtitle)
                )

                Card(
                    shape = ShapeExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    val isDefaultRelay = uiState.relayHost == "agentapprove-relay.ismamhasanovi.workers.dev"
                                    Text(
                                        text = if (isDefaultRelay) stringResource(R.string.settings_relay_server_default) else stringResource(R.string.settings_relay_server_custom),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDefaultRelay) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = uiState.relayHost,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonospaceFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_pair_id),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = uiState.pairId.take(16) + if (uiState.pairId.length > 16) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonospaceFont,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_pair_id_label), uiState.pairId))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_pair_id_copied))
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.settings_copy_pair_id),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showUnpairDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = ShapeFull,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = RiskCriticalColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_unpair_btn),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // 4. ABOUT
                Card(
                    shape = ShapeLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_about_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_about_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Confirmation dialog for unpairing
        if (showUnpairDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.settings_unpair_dialog_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.settings_unpair_dialog_msg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnpairDialog = false
                            viewModel.unpair(onSuccess = onUnpaired)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskCriticalColor)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_unpair_confirm),
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnpairDialog = false }) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                shape = ShapeExtraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    statusLabel: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(ShapeFull)
                .background(
                    if (isGranted) RiskLowColor.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.errorContainer
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = statusLabel ?: if (isGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_denied),
                color = if (isGranted) RiskLowColor else MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonospaceFont
            )
        }
    }
}
