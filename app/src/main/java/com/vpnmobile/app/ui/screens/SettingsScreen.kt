package com.vpnmobile.app.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.ui.MainViewModel

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
    val customDns by viewModel.customDns.collectAsStateWithLifecycle()

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
            Text("←", color = Orange, fontSize = 20.sp, modifier = Modifier.clickable { onBack() })
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
            subtitle = "Block internet when VPN disconnects",
            checked = killSwitch,
            onCheckedChange = { viewModel.setKillSwitch(it) }
        )

        DividerLine()

        SettingsToggleRow(
            title = "Split Tunnel",
            subtitle = "Route only selected apps through VPN",
            checked = splitEnabled,
            onCheckedChange = { viewModel.setSplitEnabled(it) }
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
                Text("Version 1.0.0", color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Features:", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("• SS / VLESS / VMESS / Trojan support", color = Gray, fontSize = 11.sp)
                Text("• WARP auto-configuration", color = Gray, fontSize = 11.sp)
                Text("• Split tunneling", color = Gray, fontSize = 11.sp)
                Text("• Kill switch", color = Gray, fontSize = 11.sp)
                Text("• Auto-reconnect", color = Gray, fontSize = 11.sp)
                Text("• Server favorites", color = Gray, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
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
private fun DividerLine() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2C)))
    Spacer(Modifier.height(10.dp))
}
