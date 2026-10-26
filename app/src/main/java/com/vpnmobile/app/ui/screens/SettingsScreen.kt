package com.vpnmobile.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.BuildConfig
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.components.DnsCheckDialog
import com.vpnmobile.app.util.Diagnostics
import com.vpnmobile.app.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgBlack: Color
    @Composable get() = AppTheme.colors.bg
private val CardBg: Color
    @Composable get() = AppTheme.colors.surface
private val Orange: Color
    @Composable get() = AppTheme.colors.accent
private val Gray: Color
    @Composable get() = AppTheme.colors.textSecondary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSplitTunnel: () -> Unit = {},
) {
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val killSwitch by viewModel.killSwitch.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val splitEnabled by viewModel.splitEnabled.collectAsStateWithLifecycle()
    val blockIpv6 by viewModel.blockIpv6.collectAsStateWithLifecycle()
    val customDns by viewModel.customDns.collectAsStateWithLifecycle()
    val biometricLock by viewModel.biometricLock.collectAsStateWithLifecycle()
    val secureScreen by viewModel.secureScreen.collectAsStateWithLifecycle()
    val hideKeys by viewModel.hideKeys.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val autoSortByLatency by viewModel.autoSortByLatency.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDnsCheck by remember { mutableStateOf(false) }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun openSystem(action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Settings screen not available", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(22.dp), Orange)
            }
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = AppTheme.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // Appearance
        SectionHeader("Appearance")

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                FilterChip(
                    selected = themeMode == value,
                    onClick = { viewModel.setThemeMode(value) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange.copy(alpha = 0.18f),
                        selectedLabelColor = Orange,
                        labelColor = Gray,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("system" to "System", "en" to "English", "my" to "မြန်မာ").forEach { (value, label) ->
                FilterChip(
                    selected = language == value,
                    onClick = { viewModel.setLanguage(value) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange.copy(alpha = 0.18f),
                        selectedLabelColor = Orange,
                        labelColor = Gray,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("orange" to "Orange", "green" to "Green", "blue" to "Blue").forEach { (value, label) ->
                FilterChip(
                    selected = accentColor == value,
                    onClick = { viewModel.setAccentColor(value) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange.copy(alpha = 0.18f),
                        selectedLabelColor = Orange,
                        labelColor = Gray,
                    ),
                )
            }
        }

        DividerLine()
        SectionHeader("VPN Settings")

        SettingsToggleRow(
            title = "Auto Connect",
            subtitle = "Connect VPN on app start",
            checked = autoConnect,
            onCheckedChange = { viewModel.setAutoConnect(it) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Auto Reconnect",
            subtitle = "Reconnect when VPN drops",
            checked = autoReconnect,
            onCheckedChange = { viewModel.setAutoReconnect(it) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Kill Switch",
            subtitle = "Block internet if the tunnel drops",
            checked = killSwitch,
            onCheckedChange = { viewModel.setKillSwitch(it) }
        )

        DividerLine()

        SettingsActionRow(
            title = "Always-on VPN",
            subtitle = "Enable Always-on + Block connections without VPN",
            action = "Open",
            onClick = { openSystem(Settings.ACTION_VPN_SETTINGS) }
        )

        DividerLine()

        SettingsActionRow(
            title = "Battery optimization",
            subtitle = "Allow the VPN to stay connected in background",
            action = "Open",
            onClick = { openSystem(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Split Tunnel",
            subtitle = "Route only selected apps through VPN",
            checked = splitEnabled,
            onCheckedChange = { viewModel.setSplitEnabled(it) }
        )

        SettingsActionRow(
            title = "Split Tunnel apps",
            subtitle = "Choose apps to include or exclude",
            action = "Manage",
            onClick = onOpenSplitTunnel
        )

        DividerLine()

        SettingsToggleRow(
            title = "Block IPv6",
            subtitle = "Route IPv6 into the tunnel to prevent leaks",
            checked = blockIpv6,
            onCheckedChange = { viewModel.setBlockIpv6(it) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Auto-sort by latency",
            subtitle = "Sort servers by lowest ping automatically",
            checked = autoSortByLatency,
            onCheckedChange = { viewModel.setAutoSortByLatency(it) }
        )

        Spacer(Modifier.height(16.dp))

        // DNS Settings
        SectionHeader("DNS Settings")

        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .padding(16.dp)
        ) {
            Column {
                Text("Custom DNS", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(customDns, color = Gray, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        SettingsActionRow(
            title = "Private DNS (DoH)",
            subtitle = "Encrypt DNS system-wide (e.g. one.one.one.one)",
            action = "Open",
            onClick = {
                if (Build.VERSION.SDK_INT >= 28) openSystem("android.settings.PRIVATE_DNS_SETTINGS")
                else openSystem(Settings.ACTION_WIRELESS_SETTINGS)
            }
        )

        DividerLine()

        SettingsActionRow(
            title = "Connection & DNS check",
            subtitle = "Show public IP and the DNS resolver in use",
            action = "Run",
            onClick = { showDnsCheck = true }
        )

        Spacer(Modifier.height(16.dp))

        // Security
        SectionHeader("Security")

        SettingsToggleRow(
            title = "App lock",
            subtitle = if (biometricAvailable) "Require biometrics to open VortexHub" else "Biometric unlock is not available on this device",
            checked = biometricLock,
            onCheckedChange = {
                if (it && !biometricAvailable) {
                    Toast.makeText(context, "Set up a fingerprint or face unlock first", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setBiometricLock(it)
                }
            }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Block screenshots",
            subtitle = "Hide app content in screenshots and Recents",
            checked = secureScreen,
            onCheckedChange = { viewModel.setSecureScreen(it) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Hide keys by default",
            subtitle = "Mask configs and QR codes until you reveal them",
            checked = hideKeys,
            onCheckedChange = { viewModel.setHideKeys(it) }
        )

        Spacer(Modifier.height(16.dp))

        // Diagnostics
        SectionHeader("Diagnostics")

        SettingsActionRow(
            title = "Export diagnostics",
            subtitle = "Redacted connection log for troubleshooting",
            action = "Export",
            onClick = {
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { Diagnostics.export(context) }
                        ShareUtils.shareFile(context, file, "text/plain", "VortexHub diagnostics")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // About
        SectionHeader("About")

        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .padding(16.dp)
        ) {
            Column {
                Text("VortexHub VPN", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Version ${BuildConfig.VERSION_NAME}", color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Features:", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("• WireGuard / Shadowsocks / WARP", color = Gray, fontSize = 11.sp)
                Text("• Subscriptions, QR export, fastest server", color = Gray, fontSize = 11.sp)
                Text("• Split tunneling + kill switch", color = Gray, fontSize = 11.sp)
                Text("• Auto-reconnect + diagnostics", color = Gray, fontSize = 11.sp)
                Text("• Server favorites & history", color = Gray, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDnsCheck) {
        DnsCheckDialog(onDismiss = { showDnsCheck = false })
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Orange,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colors.text,
                checkedTrackColor = Orange,
                uncheckedThumbColor = AppTheme.colors.text,
                uncheckedTrackColor = AppTheme.colors.border,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Text(action, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DividerLine() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.border))
    Spacer(Modifier.height(10.dp))
}
