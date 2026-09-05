package com.vpnmobile.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.R
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.PowerIcon
import com.vpnmobile.app.vpn.VpnState
import kotlinx.coroutines.delay

private val BgBlack = Color(0xFF0A0A0A)
private val CardBg = Color(0xFF1C1C1E)
private val Orange = Color(0xFFFF8C00)
private val OrangeBg = Color(0xFF3A2A14)
private val GrayText = Color(0xFF8E8E93)
private val LightBg = Color(0xFFE8E0D0)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    needsPermission: Boolean,
    onConnect: (TunnelProfile) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val connectedProfile by viewModel.connectedProfile.collectAsStateWithLifecycle()
    val freeSelected by viewModel.freeSelected.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()
    val pingLoading by viewModel.pingLoading.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.state) {
        while (status.state == VpnState.CONNECTED) { now = System.currentTimeMillis(); delay(1000) }
    }
    val targetProfile = freeSelected ?: selectedProfile ?: profiles.firstOrNull()
    var gameMode by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(false) }
    var bestServer by remember { mutableStateOf(false) }
    var iranMode by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Orange, Color(0xFFFF6B00)))), contentAlignment = Alignment.Center) { Text("V", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(6.dp))
                    Text("Vortex", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Hub", color = Orange, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                Text("≡", color = GrayText, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Text("⚙", color = GrayText, fontSize = 22.sp)
            }
            Box(
                Modifier.padding(horizontal = 14.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF2A2520), Color(0xFF1C1C1E)))).padding(16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("↓", color = GrayText, fontSize = 14.sp); Text("Download", color = GrayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Upload", color = GrayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("↑", color = GrayText, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2C)))
                    Spacer(Modifier.height(12.dp))
                    ToggleRow("Game Mode", "Lower ping for smoother gaming", gameMode) { gameMode = it }
                    DividerLine()
                    ToggleRow("Auto Start Stop", "Reduces battery", autoStart) { autoStart = it }
                    DividerLine()
                    ToggleRow("Set Best Server", "Lowest Ping", bestServer) { bestServer = it }
                    DividerLine()
                    ToggleRow("Iran routing mode", "Iran direct, everything else through proxy", iranMode) { iranMode = it }
                    DividerLine()
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🚀", fontSize = 14.sp)
                                Text(connectedProfile?.name ?: targetProfile?.name ?: "Marz (test1) [VMess - ws]", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            // IP hidden - not shown per user request
                            Spacer(Modifier.height(2.dp))
                        }
                        val tp = targetProfile
                        val pr = tp?.let { pingResults[it.id] }
                        val loading = tp?.let { pingLoading.contains(it.id) } ?: false
                        when {
                            status.state != VpnState.DISCONNECTED -> Text(when (status.state) { VpnState.CONNECTED -> "✓ Connected"; VpnState.CONNECTING -> "Connecting..."; else -> "" }, color = Orange, fontSize = 11.sp)
                            loading -> Text("Checking...", color = Orange, fontSize = 11.sp)
                            pr != null -> Text(if (pr.success) "${pr.ms}ms ✓" else "Failed ✗", color = if (pr.success) Color(0xFF00C853) else Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { tp?.let { viewModel.testPing(it) } })
                            else -> Text("Tap to Ping Again", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { tp?.let { viewModel.testPing(it) } })
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            VortexPowerButton(state = status.state, onClick = {
                when (status.state) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> viewModel.disconnect()
                    VpnState.DISCONNECTED -> { val p = targetProfile; if (p != null) onConnect(p) else onOpenServers() }
                }
            })
            Spacer(Modifier.height(18.dp))
            Text(
                text = when (status.state) { VpnState.CONNECTED -> "Connected"; VpnState.CONNECTING -> "Connecting..."; else -> "Disconnected" },
                color = GrayText, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (status.state) { VpnState.CONNECTED -> "Connected for ${ConfigTools.formatDuration(now - status.connectedAtMillis)}"; else -> "Select a server & tap to start" },
                color = GrayText.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            if (needsPermission) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A1A1A)).padding(12.dp).clickable { onRequestVpnPermission() }) {
                    Text("VPN permission required — Tap to grant", color = Color(0xFFFF6B6B), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            if (status.state == VpnState.CONNECTED) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMini("↓", ConfigTools.formatBytes(status.rxBytes), Modifier.weight(1f))
                    StatMini("↑", ConfigTools.formatBytes(status.txBytes), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                RealTimeLatencyCard()
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DividerLine() { Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2C))); Spacer(Modifier.height(10.dp)) }

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) { Text("i", color = GrayText, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            Text(subtitle, color = GrayText, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Orange, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3A3A3C), uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun VortexPowerButton(state: VpnState, onClick: () -> Unit) {
    val connected = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val infinite = rememberInfiniteTransition(label = "pow")
    val pulse by infinite.animateFloat(initialValue = 0.96f, targetValue = 1.06f, animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val glow by infinite.animateFloat(initialValue = 0.15f, targetValue = 0.35f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val rotate by infinite.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(3000, easing = androidx.compose.animation.core.LinearEasing)), label = "rot")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.90f else 1f, tween(100), label = "scale")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp)) {
        if (connected) {
            Box(Modifier.size(220.dp).scale(pulse).alpha(glow).clip(CircleShape).background(Brush.radialGradient(listOf(Orange.copy(0.35f), Color.Transparent))))
            Box(Modifier.size(200.dp).scale(pulse * 0.92f).alpha(glow * 0.7f).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF6B00).copy(0.25f), Color.Transparent))))
            repeat(8) { i ->
                val ang = rotate + i * 45f
                val r = 105f
                val x = kotlin.math.cos(Math.toRadians(ang.toDouble())).toFloat() * r
                val y = kotlin.math.sin(Math.toRadians(ang.toDouble())).toFloat() * r
                Box(Modifier.size(7.dp).padding(0.dp).scale(pulse).alpha(0.9f).clip(CircleShape).background(Orange).graphicsLayer { translationX = x; translationY = y })
            }
        }
        if (connecting) {
            Box(Modifier.size(200.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Orange, Color(0xFFFF6B00), Orange))).graphicsLayer { rotationZ = rotate })
        }
        val outer = if (connected) Brush.sweepGradient(listOf(Orange, Color(0xFFFF6B00), Color(0xFFFFD700), Orange)) else Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF1C1C1E)))
        Box(
            modifier = Modifier.size(190.dp).clip(CircleShape).background(outer).clickable(interactionSource = interaction, indication = null, onClick = onClick).scale(scale * if (connected) pulse * 0.98f else 1f).graphicsLayer { if (connected) rotationZ = rotate * 0.15f },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(164.dp).clip(CircleShape).background(if (connected) Brush.radialGradient(listOf(Color(0xFFFFF8E7), LightBg)) else Brush.linearGradient(listOf(LightBg, Color(0xFFE8E0D0)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { if (connected) rotationZ = -rotate * 0.15f }) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (connected) Orange.copy(0.18f) else Color(0xFF1C1C1E).copy(0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (connected) Text("✓", color = Orange, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        else Icon(PowerIcon, null, Modifier.size(32.dp), Color(0xFF1C1C1E))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (connected) "Connected" else if (connecting) "Connecting..." else "Tap to Connect", color = if (connected) Orange else Color(0xFF1C1C1E), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun StatMini(icon: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, color = GrayText, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RealTimeLatencyCard() {
    var latency by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val r = com.vpnmobile.app.data.PingTester.ping("[Interface]\nPrivateKey=test\n[Peer]\nEndpoint = 1.1.1.1:53")
                latency = if (r.success) r.ms else null
            } catch (_: Exception) { latency = null }
            delay(3000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (latency != null && latency!! < 100) Color(0xFF00C853) else if (latency != null) Color(0xFFFFAB00) else GrayText))
            Text("Latency 1.1.1.1", color = GrayText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(latency?.let { "$it ms" } ?: "—", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
