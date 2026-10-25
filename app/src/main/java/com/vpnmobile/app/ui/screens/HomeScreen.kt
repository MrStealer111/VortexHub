package com.vpnmobile.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.DnsCheck
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.PowerIcon
import com.vpnmobile.app.ui.components.ProfileAvatar
import com.vpnmobile.app.vpn.VpnState
import com.vpnmobile.app.vpn.VpnStatus
import kotlinx.coroutines.delay

private val BgBlack = Color(0xFF0A0A0A)
private val CardBg = Color(0xFF1C1C1E)
private val Orange = Color(0xFFFF8C00)
private val GrayText = Color(0xFF8E8E93)
private val LightBg = Color(0xFFE8E0D0)
private val Success = Color(0xFF00C853)
private val ErrorRed = Color(0xFFFF5252)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    needsPermission: Boolean,
    onConnect: (TunnelProfile) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val connectedProfile by viewModel.connectedProfile.collectAsStateWithLifecycle()
    val freeSelected by viewModel.freeSelected.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var publicIp by remember { mutableStateOf<String?>(null) }
    var latency by remember { mutableLongStateOf(-1L) }
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
            while (true) {
                publicIp = DnsCheck.egressIp()
                delay(60_000)
            }
        } else {
            publicIp = null
        }
    }

    LaunchedEffect(status.state, connectedProfile?.id) {
        if (status.state == VpnState.CONNECTED) {
            while (true) {
                val config = connectedProfile?.configText ?: "Endpoint = 1.1.1.1:53"
                val result = runCatching { PingTester.ping(config) }.getOrNull()
                latency = if (result?.success == true) result.ms.toLong() else -1L
                delay(5_000)
            }
        } else {
            latency = -1L
        }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(onOpenServers = onOpenServers, onOpenSettings = onOpenSettings)

            AnimatedVisibility(
                visible = status.state == VpnState.CONNECTED,
                enter = slideInVertically(
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it },
                ) + expandVertically(
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(280)),
                exit = slideOutVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    targetOffsetY = { -it },
                ) + shrinkVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(180)),
            ) {
                ConnectionDetailCard(
                    status = status,
                    profile = connectedProfile,
                    publicIp = publicIp,
                    latency = latency,
                    durationMillis = (now - status.connectedAtMillis).coerceAtLeast(0),
                )
            }

            ServerCard(
                name = displayProfile?.name ?: "Select a server",
                subtitle = displayProfile?.let { "${ConfigTools.protocolLabel(it.configText)} · ${ConfigTools.endpointPreview(it.configText)}" } ?: "Tap to choose a server",
                flag = displayProfile?.flag ?: "\uD83C\uDF10",
                state = status.state,
                onManage = onOpenServers,
            )

            Spacer(Modifier.weight(1f))
            VortexPowerButton(state = status.state, onClick = {
                when (status.state) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> viewModel.disconnect()
                    VpnState.BLOCKED -> { val p = targetProfile; if (p != null) viewModel.reconnect(p) else viewModel.disconnect() }
                    VpnState.DISCONNECTED -> { val p = targetProfile; if (p != null) onConnect(p) else onOpenServers() }
                }
            })
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(
                        when (status.state) {
                            VpnState.CONNECTED -> Success
                            VpnState.CONNECTING -> Orange
                            VpnState.BLOCKED -> ErrorRed
                            else -> GrayText.copy(alpha = 0.5f)
                        }
                    )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when (status.state) {
                        VpnState.CONNECTED -> "Connected"
                        VpnState.CONNECTING -> "Connecting..."
                        VpnState.BLOCKED -> "Blocked"
                        else -> "Disconnected"
                    },
                    color = when (status.state) {
                        VpnState.CONNECTED -> Success
                        VpnState.CONNECTING -> Orange
                        VpnState.BLOCKED -> ErrorRed
                        else -> GrayText
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (status.state) {
                    VpnState.CONNECTED -> "Traffic is encrypted end-to-end"
                    VpnState.CONNECTING -> "Negotiating secure tunnel..."
                    VpnState.BLOCKED -> "Kill switch active — traffic is blocked"
                    else -> "Select a server & tap to start"
                },
                color = GrayText.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            if (needsPermission) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A1A1A)).clickable { onRequestVpnPermission() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp), Color(0xFFFF6B6B))
                    Spacer(Modifier.width(8.dp))
                    Text("VPN permission required — Tap to grant", color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeTopBar(onOpenServers: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Orange, Color(0xFFFF6B00)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Shield, null, Modifier.size(19.dp), Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("Vortex", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("Hub", color = Orange, fontSize = 23.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenServers, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Dns, "Servers", Modifier.size(21.dp), GrayText)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Settings, "Settings", Modifier.size(21.dp), GrayText)
            }
        }
    }
}

@Composable
private fun ConnectionDetailCard(
    status: VpnStatus,
    profile: TunnelProfile?,
    publicIp: String?,
    latency: Long,
    durationMillis: Long,
) {
    val infinite = rememberInfiniteTransition(label = "detail")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot",
    )
    val borderAlpha by infinite.animateFloat(
        initialValue = 0.16f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "border",
    )
    val shape = RoundedCornerShape(22.dp)
    val protocol = profile?.let { ConfigTools.protocolLabel(it.configText) } ?: "VPN"
    val locationName = profile?.name ?: "VPN server"
    val locationFlag = profile?.flag ?: "\uD83C\uDF10"

    Box(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF211A10), Color(0xFF141414))))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Orange.copy(alpha = borderAlpha), Color.White.copy(alpha = 0.05f), Orange.copy(alpha = borderAlpha * 0.45f))
                ),
                shape,
            )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).alpha(dotAlpha).clip(CircleShape).background(Color(0xFF00E676)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "SECURE TUNNEL",
                    color = Color(0xFFFFB74D), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(14.dp), Color(0xFF69F0AE))
                Spacer(Modifier.width(4.dp))
                Text("ENCRYPTED", color = GrayText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("PUBLIC IP", color = GrayText.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        publicIp ?: "Checking...",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.95f)) {
                    Text("LOCATION", color = GrayText.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(locationFlag, contentSize = 14.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            locationName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailStat(Icons.Rounded.ArrowDownward, ConfigTools.formatBytes(status.rxBytes), "Download", Color(0xFF4FC3F7), Modifier.weight(1f))
                DetailStat(Icons.Rounded.ArrowUpward, ConfigTools.formatBytes(status.txBytes), "Upload", Color(0xFF69F0AE), Modifier.weight(1f))
                DetailStat(Icons.Rounded.Timer, ConfigTools.formatDuration(durationMillis), "Duration", Orange, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, Modifier.size(13.dp), if (latency >= 0) Color(0xFF69F0AE) else GrayText)
                Spacer(Modifier.width(5.dp))
                Text("Ping", color = GrayText, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(if (latency >= 0) "$latency ms" else "—", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), Orange)
                Spacer(Modifier.width(4.dp))
                Text(protocol, color = Color(0xFFFFB74D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailStat(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.045f)).padding(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(15.dp), tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(1.dp))
        Text(label, color = GrayText.copy(alpha = 0.75f), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun ServerCard(
    name: String,
    subtitle: String,
    flag: String,
    state: VpnState,
    onManage: () -> Unit,
) {
    val connected = state == VpnState.CONNECTED
    Box(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (connected) Color(0xFF1E1B15) else CardBg)
            .clickable { onManage() }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Orange.copy(alpha = 0.22f), Color(0xFF2C2C2E)))).padding(1.dp)
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)).background(CardBg),
                        contentAlignment = Alignment.Center,
                    ) { ProfileAvatar(flag, contentSize = 20.dp) }
                }
                if (connected) {
                    Box(
                        Modifier.size(11.dp).align(Alignment.BottomEnd).clip(CircleShape)
                            .background(Success).border(2.dp, CardBg, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = GrayText, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (state) {
                        VpnState.CONNECTED -> {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), Success)
                            Spacer(Modifier.width(4.dp))
                            Text("Connected", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        VpnState.CONNECTING -> {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Orange)
                            Spacer(Modifier.width(5.dp))
                            Text("Connecting", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        VpnState.BLOCKED -> {
                            Icon(Icons.Rounded.Warning, null, Modifier.size(14.dp), ErrorRed)
                            Spacer(Modifier.width(4.dp))
                            Text("Blocked", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        VpnState.DISCONNECTED -> Text("Manage", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(15.dp), GrayText)
            }
        }
    }
}

@Composable
private fun VortexPowerButton(state: VpnState, onClick: () -> Unit) {
    val connected = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val blocked = state == VpnState.BLOCKED
    val infinite = rememberInfiniteTransition(label = "pow")
    val pulse by infinite.animateFloat(initialValue = 0.96f, targetValue = 1.06f, animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val glow by infinite.animateFloat(initialValue = 0.15f, targetValue = 0.35f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val rotate by infinite.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "rot")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, tween(100), label = "scale")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp)) {
        if (connected) {
            Box(Modifier.size(220.dp).scale(pulse).alpha(glow).clip(CircleShape).background(Brush.radialGradient(listOf(Orange.copy(0.35f), Color.Transparent))))
            Box(Modifier.size(200.dp).scale(pulse * 0.92f).alpha(glow * 0.7f).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF6B00).copy(0.25f), Color.Transparent))))
            repeat(8) { i ->
                val ang = rotate + i * 45f
                val r = 105f
                val x = kotlin.math.cos(Math.toRadians(ang.toDouble())).toFloat() * r
                val y = kotlin.math.sin(Math.toRadians(ang.toDouble())).toFloat() * r
                Box(Modifier.size(7.dp).scale(pulse).alpha(0.9f).clip(CircleShape).background(Orange).graphicsLayer { translationX = x; translationY = y })
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
                        when {
                            connected -> Icon(Icons.Rounded.Check, null, Modifier.size(30.dp), Orange)
                            blocked -> Icon(Icons.Rounded.Refresh, null, Modifier.size(28.dp), Color(0xFF1C1C1E))
                            connecting -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp, color = Orange)
                            else -> Icon(PowerIcon, null, Modifier.size(32.dp), Color(0xFF1C1C1E))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (connected) "Connected" else if (connecting) "Connecting..." else if (blocked) "Retry" else "Tap to Connect",
                        color = if (connected || blocked) Orange else Color(0xFF1C1C1E),
                        fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}
