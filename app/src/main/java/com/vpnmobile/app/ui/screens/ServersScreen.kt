package com.vpnmobile.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.ServerApi
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.vpn.VpnState
import kotlinx.coroutines.launch

private val BgBlack = Color(0xFF0A0A0A)
private val CardBg = Color(0xFF1E1E1E)
private val Orange = Color(0xFFFF8C00)
private val Gray = Color(0xFF8E8E93)
private val Border = Color(0xFF2C2C2E)

data class FreeServer(val name: String, val flag: String, val ms: Int, val country: String)

private val freeServers = listOf(
    FreeServer("WarpGuard", "🌐", 48, "VLESS"),
)

@Composable
fun ServersScreen(
    viewModel: MainViewModel,
    onConnect: (TunnelProfile) -> Unit,
    onImport: () -> Unit,
    onWarpGenerate: (() -> Unit)? = null,
    onQrScan: (() -> Unit)? = null,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val selected by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val freeSelected by viewModel.freeSelected.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Free") }
    var sortByTime by remember { mutableStateOf(false) }

    var showPlusMenu by remember { mutableStateOf(false) }
    var showManualSub by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var apiFree by remember { mutableStateOf<List<ServerApi.ServerEntry>>(emptyList()) }
    var freePings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var freeLoading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(serverUrl, refreshTick) {
        val fetched = ServerApi.fetchFree(context, serverUrl)
        apiFree = if (fetched.isNotEmpty()) fetched else freeServers.map { ServerApi.ServerEntry(it.name, it.flag, "vless://free@vortexhub.net:443#${it.name}", false) }
    }

    Column(Modifier.fillMaxSize().background(BgBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Configs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { profiles.forEach { viewModel.deleteProfile(it) } }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Box {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { showPlusMenu = true }, contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    DropdownMenu(expanded = showPlusMenu, onDismissRequest = { showPlusMenu = false; showManualSub = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                        DropdownMenuItem(text = { Text("Import from Clipboard", color = Color.White, fontSize = 13.sp) }, onClick = {
                            showPlusMenu = false
                            val txt = clipboard.getText()?.text ?: ""
                            if (txt.isNotBlank()) viewModel.addProfile("Imported", "🌐", txt)
                        })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Import url from Clipboard", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; val t = clipboard.getText()?.text ?: ""; if (t.isNotBlank()) viewModel.addProfile("URL Import", "🌐", t) })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Import Sub from Clipboard", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Scan config from QR Code", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; (onQrScan ?: onImport).invoke() })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Update Subs", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; refreshTick++ })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Add manual config", color = Color.White, fontSize = 13.sp); Text(if (showManualSub) "∧" else "∨", color = Color.White) } }, onClick = { showManualSub = !showManualSub })
                        if (showManualSub) {
                            HorizontalDivider(color = Color(0xFF2C2C2E))
                            listOf("socks", "hysteria2", "shadowsocks", "trojan", "vless", "vmess", "wireguard", "http", "proxy chain", "ssh", "dnstt").forEach { p ->
                                DropdownMenuItem(text = { Text("Add manual $p", color = Color.White, fontSize = 12.sp) }, onClick = { showPlusMenu = false; showManualSub = false; onImport() })
                            }
                        }
                    }
                }
                IconButton(onClick = { (onQrScan ?: onImport).invoke() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFF1E1E1E)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Free") Color(0xFF3A2A14) else Color.Transparent).clickable { tab = "Free" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Free", color = if (tab == "Free") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Favorites") Color(0xFF3A2A14) else Color.Transparent).clickable { tab = "Favorites" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("★ Favorites", color = if (tab == "Favorites") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Personal") Color(0xFF3A2A14) else Color.Transparent).clickable { tab = "Personal" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Personal", color = if (tab == "Personal") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                onClick = {
                    if (tab == "Free") {
                        if (freeLoading) return@Card
                        freeLoading = true
                        scope.launch {
                            val results = mutableMapOf<String, Int>()
                            for (s in apiFree) {
                                val r = PingTester.ping(s.uri)
                                results[s.name + s.uri] = if (r.success) r.ms else 9999
                            }
                            freePings = results
                            freeLoading = false
                        }
                    } else viewModel.testAllPings()
                },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (freeLoading) "◷" else "◷", color = Gray, fontSize = 14.sp); Text(if (freeLoading) "Checking..." else "Check Pings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Card(
                onClick = { sortByTime = !sortByTime },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("↕", color = Gray, fontSize = 14.sp); Text(if (sortByTime) "Sort By Ping" else "Sort By Time", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f)); Text("∨", color = Gray, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == "Free") {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val list = if (sortByTime) apiFree.sortedBy { freePings[it.name + it.uri] ?: 9999 } else apiFree
                if (list.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Loading free servers...", color = Gray) } }
                }
                items(list, key = { it.name + it.uri }) { s ->
                    val ms = freePings[s.name + s.uri] ?: freeServers.find { it.name == s.name }?.ms ?: 0
                    val color = when { ms == 0 || ms >= 9999 -> Gray; ms < 500 -> Color(0xFF00C853); ms < 650 -> Color(0xFFFFEB3B); else -> Color(0xFFFF5252) }
                    val displayMs = when { ms == 0 -> "Tap Check"; ms >= 9999 -> "Failed"; else -> "$ms ms" }
                    val isFreeSelected = freeSelected?.name == s.name && freeSelected?.configText == s.uri
                    val isFreeConnected = isFreeSelected && status.state == VpnState.CONNECTED
                    Card(
                        onClick = {
                            val p = TunnelProfile(id = "free-${s.name}", name = s.name, flag = s.flag, configText = s.uri, createdAt = System.currentTimeMillis())
                            viewModel.connectFree(p)
                        },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isFreeSelected) Color(0xFF2A2520) else CardBg), border = BorderStroke(1.dp, if (isFreeSelected) Orange.copy(alpha = 0.4f) else Border)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) { Text(s.flag, fontSize = 18.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("VLESS . $displayMs", color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (isFreeConnected) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF00C853).copy(0.15f)), contentAlignment = Alignment.Center) { Text("✓", color = Color(0xFF00C853), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            else if (freeLoading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Orange) else Text("›", color = Gray, fontSize = 18.sp)
                        }
                    }
                }
            }
        } else if (tab == "Favorites") {
            val favProfiles = profiles.filter { it.id in favorites }
            if (favProfiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No favorite servers", color = Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap ★ on any server to add to favorites", color = Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favProfiles, key = { it.id }) { p ->
                        val ping = pingResults[p.id]
                        val pingText = when { ping == null -> "Tap ⟡"; !ping.success -> "Failed"; else -> "${ping.ms} ms" }
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> Color(0xFF00C853); else -> Color(0xFFFFAB00) }
                        val proto = when { p.configText.startsWith("ss://", true) -> "SS"; p.configText.startsWith("vless://", true) -> "VLESS"; p.configText.startsWith("vmess://", true) -> "VMESS"; p.configText.startsWith("trojan://", true) -> "TROJAN"; else -> "WIREGUARD" }
                        val isSelected = p.id == selected?.id
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2A2520) else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.flag, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(proto, color = Gray, fontSize = 11.sp)
                                        Text(pingText, color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("★", color = Orange, fontSize = 16.sp, modifier = Modifier.clickable { viewModel.toggleFavorite(p) })
                                    Text("⟡", color = Gray, fontSize = 16.sp, modifier = Modifier.clickable { viewModel.testPing(p) })
                                    Icon(Icons.Filled.Delete, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { viewModel.deleteProfile(p) })
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No personal configs", color = Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to add SS / VLESS / VMESS", color = Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(profiles, key = { it.id }) { p ->
                        val ping = pingResults[p.id]
                        val pingText = when { ping == null -> "Tap ⟡"; !ping.success -> "Failed"; else -> "${ping.ms} ms" }
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> Color(0xFF00C853); else -> Color(0xFFFFAB00) }
                        val proto = when { p.configText.startsWith("ss://", true) -> "SS"; p.configText.startsWith("vless://", true) -> "VLESS"; p.configText.startsWith("vmess://", true) -> "VMESS"; p.configText.startsWith("trojan://", true) -> "TROJAN"; else -> "WIREGUARD" }
                        val isSelected = p.id == selected?.id
                        val isFav = p.id in favorites
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2A2520) else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.flag, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(proto, color = Gray, fontSize = 11.sp)
                                        Text(pingText, color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("★", color = if (isFav) Orange else Gray, fontSize = 16.sp, modifier = Modifier.clickable { viewModel.toggleFavorite(p) })
                                    Text("⟡", color = Gray, fontSize = 16.sp, modifier = Modifier.clickable { viewModel.testPing(p) })
                                    Icon(Icons.Filled.Delete, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { viewModel.deleteProfile(p) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
