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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.vpnmobile.app.ui.components.ProfileAvatar
import com.vpnmobile.app.ui.components.QrShareDialog
import com.vpnmobile.app.vpn.VpnState
import kotlinx.coroutines.launch
import java.net.URL

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
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val subscriptionRefreshing by viewModel.subscriptionRefreshing.collectAsStateWithLifecycle()
    val findingFastest by viewModel.findingFastest.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Free") }
    var sortByPing by remember { mutableStateOf(false) }

    var showPlusMenu by remember { mutableStateOf(false) }
    var showManualSub by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    var subUrl by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }
    var qrProfile by remember { mutableStateOf<TunnelProfile?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var apiFree by remember { mutableStateOf<List<ServerApi.ServerEntry>>(emptyList()) }
    var freePings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var freeLoading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(serverUrl, refreshTick) {
        val fetched = ServerApi.fetchFree(context, serverUrl)
        apiFree = if (fetched.isNotEmpty()) fetched else freeServers.map { ServerApi.ServerEntry(it.name, it.flag, "warp://auto", false) }
    }

    Column(Modifier.fillMaxSize().background(BgBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Configs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { profiles.forEach { viewModel.deleteProfile(it) } }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Box {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { showPlusMenu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), Color.White) }
                    DropdownMenu(expanded = showPlusMenu, onDismissRequest = { showPlusMenu = false; showManualSub = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                        DropdownMenuItem(text = { Text("Import from Clipboard", color = Color.White, fontSize = 13.sp) }, onClick = {
                            showPlusMenu = false
                            val txt = clipboard.getText()?.text ?: ""
                            if (txt.isNotBlank()) viewModel.addProfile("Imported", "🌐", txt)
                        })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Import url from Clipboard", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; val t = clipboard.getText()?.text ?: ""; if (t.isNotBlank()) viewModel.addProfile("URL Import", "🌐", t) })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Add subscription URL", color = Color.White, fontSize = 13.sp) }, onClick = {
                            showPlusMenu = false
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            subUrl = if (clip.startsWith("https://")) clip else ""
                            subName = ""
                            showAddSub = true
                        })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Scan config from QR Code", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; (onQrScan ?: onImport).invoke() })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Text("Update Subs", color = Color.White, fontSize = 13.sp) }, onClick = { showPlusMenu = false; viewModel.refreshSubscriptions(); refreshTick++ })
                        HorizontalDivider(color = Color(0xFF2C2C2E))
                        DropdownMenuItem(text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Add manual config", color = Color.White, fontSize = 13.sp); Icon(if (showManualSub) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), Color.White) } }, onClick = { showManualSub = !showManualSub })
                        if (showManualSub) {
                            HorizontalDivider(color = Color(0xFF2C2C2E))
                            listOf("wireguard", "warp", "shadowsocks").forEach { p ->
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
            ) { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), if (tab == "Favorites") Orange else Gray)
                    Spacer(Modifier.width(5.dp))
                    Text("Favorites", color = if (tab == "Favorites") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Personal") Color(0xFF3A2A14) else Color.Transparent).clickable { tab = "Personal" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Personal", color = if (tab == "Personal") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Subs") Color(0xFF3A2A14) else Color.Transparent).clickable { tab = "Subs" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Subs", color = if (tab == "Subs") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
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
                    Icon(Icons.Rounded.Timer, null, Modifier.size(14.dp), Gray); Text(if (freeLoading) "Checking..." else "Check Pings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Card(
                onClick = { sortByPing = !sortByPing },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.SwapVert, null, Modifier.size(14.dp), Gray); Text(if (sortByPing) "Sort: Ping" else "Sort: Time", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), Gray)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == "Free") {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val list = if (sortByPing) apiFree.sortedBy { freePings[it.name + it.uri] ?: 9999 } else apiFree
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
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) { ProfileAvatar(s.flag, contentSize = 18.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${com.vpnmobile.app.data.ConfigTools.protocolLabel(s.uri)} · $displayMs", color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (isFreeConnected) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF00C853).copy(0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), Color(0xFF00C853)) }
                            else if (freeLoading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Orange) else Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), Gray)
                        }
                    }
                }
            }
        } else if (tab == "Favorites") {
            val favProfiles = profiles
                .filter { it.id in favorites }
                .let { list ->
                    if (sortByPing) list.sortedBy { pingResults[it.id]?.takeIf { r -> r.success }?.ms ?: Int.MAX_VALUE }
                    else list.sortedByDescending { it.createdAt }
                }
            if (favProfiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No favorite servers", color = Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap the star on any server to add to favorites", color = Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favProfiles, key = { it.id }) { p ->
                        val ping = pingResults[p.id]
                        val pingText = when { ping == null -> "Tap ping"; !ping.success -> "Failed"; else -> "${ping.ms} ms" }
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> Color(0xFF00C853); else -> Color(0xFFFFAB00) }
                        val proto = com.vpnmobile.app.data.ConfigTools.protocolLabel(p.configText)
                        val isSelected = p.id == selected?.id
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2A2520) else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(p.flag, contentSize = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(proto, color = Gray, fontSize = 11.sp)
                                        Text(pingText, color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.Star, null, Modifier.size(18.dp).clickable { viewModel.toggleFavorite(p) }, Orange)
                                    Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp).clickable { viewModel.testPing(p) }, Gray)
                                    Icon(Icons.Filled.QrCode2, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { qrProfile = p })
                                    Icon(Icons.Filled.Delete, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { viewModel.deleteProfile(p) })
                                }
                            }
                        }
                    }
                }
            }
        } else if (tab == "Subs") {
            if (subscriptions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No subscriptions", color = Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Add an https:// subscription URL", color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Card(
                            onClick = { subUrl = ""; subName = ""; showAddSub = true },
                            shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(15.dp), Orange)
                                Spacer(Modifier.width(5.dp))
                                Text("Add subscription", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(
                            onClick = { subUrl = ""; subName = ""; showAddSub = true },
                            shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(15.dp), Orange)
                                Spacer(Modifier.width(5.dp))
                                Text("Add subscription", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(subscriptions, key = { it.id }) { sub ->
                        val count = profiles.count { it.subscriptionId == sub.id }
                        val isRefreshing = sub.id in subscriptionRefreshing
                        Card(
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sub.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("$count configs · ${updatedLabel(sub.lastUpdated)}", color = Gray, fontSize = 11.sp)
                                    Text(subscriptionHost(sub.url), color = Color(0xFF5C5C5E), fontSize = 10.sp, maxLines = 1)
                                }
                                if (isRefreshing) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange)
                                } else {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp).padding(horizontal = 10.dp).clickable { viewModel.refreshSubscriptions() }, Orange)
                                }
                                Icon(Icons.Filled.Delete, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { viewModel.removeSubscription(sub) })
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
                        Text("Tap + to add WireGuard / Shadowsocks / WARP", color = Gray, fontSize = 12.sp)
                    }
                }
            } else {
                val sortedProfiles = if (sortByPing) {
                    profiles.sortedBy { pingResults[it.id]?.takeIf { r -> r.success }?.ms ?: Int.MAX_VALUE }
                } else {
                    profiles.sortedByDescending { it.createdAt }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(
                            onClick = {
                                if (!findingFastest) {
                                    viewModel.findFastest { best -> if (best != null) onConnect(best) }
                                }
                            },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2014)), border = BorderStroke(1.dp, Orange.copy(alpha = 0.35f))
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Orange.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp), Orange) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Fastest server", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (findingFastest) "Testing latency..." else "Ping all and connect to the best", color = Gray, fontSize = 11.sp)
                                }
                                if (findingFastest) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange) else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), Orange)
                            }
                        }
                    }
                    items(sortedProfiles, key = { it.id }) { p ->
                        val ping = pingResults[p.id]
                        val pingText = when { ping == null -> "Tap ping"; !ping.success -> "Failed"; else -> "${ping.ms} ms" }
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> Color(0xFF00C853); else -> Color(0xFFFFAB00) }
                        val proto = com.vpnmobile.app.data.ConfigTools.protocolLabel(p.configText)
                        val isSelected = p.id == selected?.id
                        val isFav = p.id in favorites
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2A2520) else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(p.flag, contentSize = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(proto, color = Gray, fontSize = 11.sp)
                                        Text(pingText, color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(18.dp).clickable { viewModel.toggleFavorite(p) }, if (isFav) Orange else Gray)
                                    Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp).clickable { viewModel.testPing(p) }, Gray)
                                    Icon(Icons.Filled.QrCode2, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { qrProfile = p })
                                    Icon(Icons.Filled.Delete, null, tint = Gray, modifier = Modifier.size(18.dp).clickable { viewModel.deleteProfile(p) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    qrProfile?.let { profile ->
        QrShareDialog(profile = profile, onDismiss = { qrProfile = null })
    }

    if (showAddSub) {
        AlertDialog(
            onDismissRequest = { showAddSub = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Add subscription", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it },
                        label = { Text("Subscription URL (https://)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Supported contents: ss:// links, WireGuard configs, base64-encoded lists.", color = Gray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = subUrl.trim().startsWith("https://"),
                    onClick = {
                        showAddSub = false
                        viewModel.addSubscription(subUrl, subName)
                        subUrl = ""
                        subName = ""
                    },
                ) { Text("Add", color = Orange, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSub = false }) { Text("Cancel", color = Gray) }
            },
        )
    }
}

private fun subscriptionHost(url: String): String = try {
    URL(url).host
} catch (_: Exception) {
    url
}

private fun updatedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "never updated"
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "updated just now"
        minutes < 60 -> "updated ${minutes}m ago"
        minutes < 1440 -> "updated ${minutes / 60}h ago"
        else -> "updated ${minutes / 1440}d ago"
    }
}
