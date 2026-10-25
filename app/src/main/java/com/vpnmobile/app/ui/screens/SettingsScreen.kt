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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.BuildConfig
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.components.DnsCheckDialog
import com.vpnmobile.app.util.Diagnostics
import com.vpnmobile.app.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgBlack = Color(0xFF0A0A0A)
private val CardBg = Color(0xFF1E1E1E)
private val Orange = Color(0xFFFF8C00)
private val Gray = Color(0xFF8E8E93)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val killSwitch by viewModel.killSwitch.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val splitEnabled by viewModel.splitEnabled.collectAsStateWithLifecycle()
    val blockIpv6 by viewModel.blockIpv6.collectAsStateWithLifecycle()
    val customDns by viewModel.customDns.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDnsCheck by remember { mutableStateOf(false) }

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
            Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // VPN Settings
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

        DividerLine()

        SettingsToggleRow(
            title = "Block IPv6",
            subtitle = "Route IPv6 into the tunnel to prevent leaks",
            checked = blockIpv6,
            onCheckedChange = { viewModel.setBlockIpv6(it) }
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
                Text("Custom DNS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                Text("VortexHub VPN", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Orange,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF3A3A3C),
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
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Text(action, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DividerLine() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2C)))
    Spacer(Modifier.height(10.dp))
}
