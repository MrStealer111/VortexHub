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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.GeoIp
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.components.ProfileAvatar
import com.vpnmobile.app.ui.theme.AppTheme
import com.vpnmobile.app.vpn.VpnState
import com.vpnmobile.app.vpn.VpnStatus
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    needsPermission: Boolean,
    onConnect: (TunnelProfile) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val connectedProfile by viewModel.connectedProfile.collectAsStateWithLifecycle()
    val freeSelected by viewModel.freeSelected.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var geo by remember { mutableStateOf<GeoIp.Info?>(null) }
    var latency by remember { mutableLongStateOf(-1L) }
    var downNow by remember { mutableLongStateOf(0L) }
    var upNow by remember { mutableLongStateOf(0L) }
    val targetProfile = freeSelected ?: selectedProfile ?: profiles.firstOrNull()
    val displayProfile = if (status.state == VpnState.CONNECTED || status.state == VpnState.CONNECTING) {
        connectedProfile ?: targetProfile
    } else {
        targetProfile
    }

    LaunchedEffect(status.state) {
        while (status.state == VpnState.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(status.state, connectedProfile?.id) {
        if (status.state == VpnState.CONNECTED) {
            var first = true
            while (true) {
                geo = GeoIp.lookup(context, force = first)
                first = false
                delay(60_000)
            }
        } else {
            geo = null
            GeoIp.clearCache()
        }
    }
    LaunchedEffect(status.state, connectedProfile?.id) {
        if (status.state == VpnState.CONNECTED) {
            while (true) {
                val result = runCatching {
                    PingTester.ping(connectedProfile?.configText ?: "Endpoint = 1.1.1.1:53")
                }.getOrNull()
                latency = if (result?.success == true) result.ms.toLong() else -1L
                delay(5_000)
            }
        } else {
            latency = -1L
        }
    }
    LaunchedEffect(status.state) {
        if (status.state == VpnState.CONNECTED) {
            var lastRx = viewModel.status.value.rxBytes
            var lastTx = viewModel.status.value.txBytes
            var lastAt = System.currentTimeMillis()
            while (true) {
                delay(1_000)
                val current = viewModel.status.value
                if (current.state != VpnState.CONNECTED) break
                val seconds = ((System.currentTimeMillis() - lastAt).coerceAtLeast(1)) / 1000.0
                downNow = ((current.rxBytes - lastRx).coerceAtLeast(0L) / seconds).toLong()
                upNow = ((current.txBytes - lastTx).coerceAtLeast(0L) / seconds).toLong()
                lastRx = current.rxBytes
                lastTx = current.txBytes
                lastAt = System.currentTimeMillis()
            }
        } else {
            downNow = 0L
            upNow = 0L
        }
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp), AppTheme.colors.accent) }
                Spacer(Modifier.width(8.dp))
                Text("VortexHub", color = AppTheme.colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row {
                IconButton(onClick = onOpenServers) {
                    Icon(Icons.Rounded.Dns, "Servers", Modifier.size(22.dp), AppTheme.colors.textSecondary)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, "Settings", Modifier.size(22.dp), AppTheme.colors.textSecondary)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusHero(status.state) {
                when (status.state) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> viewModel.disconnect()
                    VpnState.BLOCKED -> {
                        val profile = targetProfile
                        if (profile != null) viewModel.reconnect(profile) else viewModel.disconnect()
                    }
                    VpnState.DISCONNECTED -> {
                        val profile = targetProfile
                        if (profile != null) onConnect(profile) else onOpenServers()
                    }
                }
            }

            if (status.state == VpnState.CONNECTED) {
                SessionCard(
                    publicIp = geo?.ip,
                    location = geo?.label ?: connectedProfile?.name ?: "VPN server",
                    flag = geo?.flag ?: connectedProfile?.flag ?: "\uD83C\uDF10",
                    latency = latency,
                    duration = now - status.connectedAtMillis,
                    down = downNow,
                    up = upNow,
                    rx = status.rxBytes,
                    tx = status.txBytes,
                )
            }

            ServerCard(
                name = displayProfile?.name ?: "No server selected",
                subtitle = displayProfile?.let {
                    "${ConfigTools.protocolLabel(it.configText)} · ${ConfigTools.endpointPreview(it.configText)}"
                } ?: "Choose a server to begin",
                flag = displayProfile?.flag ?: "\uD83C\uDF10",
                state = status.state,
                onClick = onOpenServers,
            )

            if (needsPermission) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.surfaceVariant)
                        .clickable { onRequestVpnPermission() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), AppTheme.colors.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "VPN permission required — tap to grant",
                        color = AppTheme.colors.error,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusHero(state: VpnState, onClick: () -> Unit) {
    val accent = when (state) {
        VpnState.CONNECTED -> AppTheme.colors.success
        VpnState.CONNECTING -> AppTheme.colors.accent
        VpnState.BLOCKED -> AppTheme.colors.error
        VpnState.DISCONNECTED -> AppTheme.colors.textSecondary
    }
    val label = when (state) {
        VpnState.CONNECTED -> "Connected"
        VpnState.CONNECTING -> "Connecting..."
        VpnState.BLOCKED -> "Blocked — tap to retry"
        VpnState.DISCONNECTED -> "Tap to connect"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.surface)
            .clickable { onClick() }
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            when (state) {
                VpnState.CONNECTED -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(44.dp), accent)
                VpnState.CONNECTING -> CircularProgressIndicator(Modifier.size(40.dp), color = accent, strokeWidth = 3.dp)
                VpnState.BLOCKED -> Icon(Icons.Rounded.Refresh, null, Modifier.size(40.dp), accent)
                VpnState.DISCONNECTED -> Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(40.dp), accent)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            when (state) {
                VpnState.CONNECTED -> "Your traffic is routed through the tunnel"
                VpnState.CONNECTING -> "Establishing the tunnel"
                VpnState.BLOCKED -> "Kill switch is blocking traffic"
                VpnState.DISCONNECTED -> "Choose a server or tap the button"
            },
            color = AppTheme.colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SessionCard(
    publicIp: String?,
    location: String,
    flag: String,
    latency: Long,
    duration: Long,
    down: Long,
    up: Long,
    rx: Long,
    tx: Long,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(flag, contentSize = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(location, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(publicIp ?: "Checking...", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Session", ConfigTools.formatDuration(duration), Modifier.weight(1f))
            Metric("Ping", if (latency >= 0) "$latency ms" else "—", Modifier.weight(1f))
            Metric("Down", ConfigTools.formatSpeed(down), Modifier.weight(1f))
            Metric("Up", ConfigTools.formatSpeed(up), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Received", ConfigTools.formatBytes(rx), Modifier.weight(1f))
            Metric("Sent", ConfigTools.formatBytes(tx), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.surfaceVariant).padding(10.dp)) {
        Text(label, color = AppTheme.colors.textSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = AppTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ServerCard(
    name: String,
    subtitle: String,
    flag: String,
    state: VpnState,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(flag, contentSize = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = AppTheme.colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
        Text(
            when (state) {
                VpnState.CONNECTED -> "Connected"
                VpnState.CONNECTING -> "Connecting"
                VpnState.BLOCKED -> "Blocked"
                VpnState.DISCONNECTED -> "Change"
            },
            color = when (state) {
                VpnState.CONNECTED -> AppTheme.colors.success
                VpnState.BLOCKED -> AppTheme.colors.error
                else -> AppTheme.colors.accent
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
