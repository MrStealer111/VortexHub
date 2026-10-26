package com.vpnmobile.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.ServerApi
import com.vpnmobile.app.data.Subscription
import com.vpnmobile.app.data.SubscriptionUsage
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.components.ProfileAvatar
import com.vpnmobile.app.ui.components.QrShareDialog
import com.vpnmobile.app.vpn.VpnState
import kotlinx.coroutines.launch
import java.net.URL
import java.util.Locale

private val BgBlack: Color
    @Composable get() = AppTheme.colors.bg
private val CardBg: Color
    @Composable get() = AppTheme.colors.surface
private val Orange: Color
    @Composable get() = AppTheme.colors.accent
private val Gray: Color
    @Composable get() = AppTheme.colors.textSecondary
private val Border: Color
    @Composable get() = AppTheme.colors.border

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
    val hideKeys by viewModel.hideKeys.collectAsStateWithLifecycle()
    val autoSortByLatency by viewModel.autoSortByLatency.collectAsStateWithLifecycle()
    val subHealth by viewModel.subHealth.collectAsStateWithLifecycle()
    val healthChecking by viewModel.healthChecking.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Free") }
    var sortByPing by remember { mutableStateOf(autoSortByLatency) }
    LaunchedEffect(autoSortByLatency) { sortByPing = autoSortByLatency }
    var query by remember { mutableStateOf("") }
    var countryFilter by remember { mutableStateOf<String?>(null) }
    var subFilter by remember { mutableStateOf<String?>(null) }

    var showPlusMenu by remember { mutableStateOf(false) }
    var showManualSub by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    var subUrl by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }
    var renameSub by remember { mutableStateOf<Subscription?>(null) }
    var renameName by remember { mutableStateOf("") }
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
            Text("Configs", color = AppTheme.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { profiles.forEach { viewModel.deleteProfile(it) } }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, null, tint = AppTheme.colors.text, modifier = Modifier.size(20.dp)) }
                Box {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colors.text.copy(alpha = 0.08f)).clickable { showPlusMenu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), AppTheme.colors.text) }
                    DropdownMenu(expanded = showPlusMenu, onDismissRequest = { showPlusMenu = false; showManualSub = false }, modifier = Modifier.background(AppTheme.colors.surface)) {
                        DropdownMenuItem(text = { Text("Import from Clipboard", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = {
                            showPlusMenu = false
                            val txt = clipboard.getText()?.text ?: ""
                            if (txt.isNotBlank()) viewModel.addProfile("Imported", "🌐", txt)
                        })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Import url from Clipboard", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = { showPlusMenu = false; val t = clipboard.getText()?.text ?: ""; if (t.isNotBlank()) viewModel.addProfile("URL Import", "🌐", t) })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Add subscription URL", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = {
                            showPlusMenu = false
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            subUrl = if (clip.startsWith("https://")) clip else ""
                            subName = ""
                            showAddSub = true
                        })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Scan config from QR Code", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = { showPlusMenu = false; (onQrScan ?: onImport).invoke() })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Update Subs", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = { showPlusMenu = false; viewModel.refreshSubscriptions(); refreshTick++ })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Export subscriptions", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = { showPlusMenu = false; clipboard.setText(AnnotatedString(viewModel.exportSubscriptions())); android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show() })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Text("Import subscriptions", color = AppTheme.colors.text, fontSize = 13.sp) }, onClick = { showPlusMenu = false; val clip = clipboard.getText()?.text?.trim().orEmpty(); if (clip.isNotBlank()) viewModel.importSubscriptions(clip) })
                        HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                        DropdownMenuItem(text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Add manual config", color = AppTheme.colors.text, fontSize = 13.sp); Icon(if (showManualSub) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), AppTheme.colors.text) } }, onClick = { showManualSub = !showManualSub })
                        if (showManualSub) {
                            HorizontalDivider(color = AppTheme.colors.surfaceVariant)
                            listOf("wireguard", "warp", "shadowsocks").forEach { p ->
                                DropdownMenuItem(text = { Text("Add manual $p", color = AppTheme.colors.text, fontSize = 12.sp) }, onClick = { showPlusMenu = false; showManualSub = false; onImport() })
                            }
                        }
                    }
                }
                IconButton(onClick = { (onQrScan ?: onImport).invoke() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.QrCodeScanner, null, tint = AppTheme.colors.text, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(AppTheme.colors.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Free") AppTheme.colors.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { tab = "Free" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Free", color = if (tab == "Free") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Favorites") AppTheme.colors.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { tab = "Favorites" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), if (tab == "Favorites") Orange else Gray)
                    Spacer(Modifier.width(5.dp))
                    Text("Favorites", color = if (tab == "Favorites") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Personal") AppTheme.colors.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { tab = "Personal" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Personal", color = if (tab == "Personal") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == "Subs") AppTheme.colors.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { tab = "Subs" }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Subs", color = if (tab == "Subs") Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
            placeholder = { Text("Search servers...", color = Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), Gray) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), Gray) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                unfocusedBorderColor = Border,
                focusedTextColor = AppTheme.colors.text,
                unfocusedTextColor = AppTheme.colors.text,
                cursorColor = Orange,
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
            ),
        )
        val flagOptions = remember(profiles, apiFree) {
            (profiles.map { it.flag } + apiFree.map { it.flag })
                .filter { it.isNotBlank() && it != "\uD83C\uDF10" }
                .distinct()
                .take(10)
        }
        if (flagOptions.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = countryFilter == null,
                    onClick = { countryFilter = null },
                    label = { Text("All", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange.copy(alpha = 0.18f),
                        selectedLabelColor = Orange,
                        labelColor = Gray,
                    ),
                )
                flagOptions.forEach { flag ->
                    FilterChip(
                        selected = countryFilter == flag,
                        onClick = { countryFilter = if (countryFilter == flag) null else flag },
                        label = { Text(flag, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Orange.copy(alpha = 0.18f),
                            selectedLabelColor = Orange,
                            labelColor = Gray,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
                    Icon(Icons.Rounded.Timer, null, Modifier.size(14.dp), Gray); Text(if (freeLoading) "Checking..." else "Check Pings", color = AppTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Card(
                onClick = { sortByPing = !sortByPing },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.SwapVert, null, Modifier.size(14.dp), Gray); Text(if (sortByPing) "Sort: Ping" else "Sort: Time", color = AppTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), Gray)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == "Free") {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val list = apiFree
                    .filter { query.isBlank() || it.name.contains(query, true) || it.flag.contains(query, true) }
                    .filter { countryFilter == null || it.flag == countryFilter }
                    .let { if (sortByPing) it.sortedBy { s -> freePings[s.name + s.uri] ?: 9999 } else it }
                if (list.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Loading free servers...", color = Gray) } }
                }
                items(list, key = { it.name + it.uri }) { s ->
                    val ms = freePings[s.name + s.uri] ?: freeServers.find { it.name == s.name }?.ms ?: 0
                    val color = when { ms == 0 || ms >= 9999 -> Gray; ms < 500 -> AppTheme.colors.success; ms < 650 -> Color(0xFFFFEB3B); else -> AppTheme.colors.error }
                    val displayMs = when { ms == 0 -> "Tap Check"; ms >= 9999 -> "Failed"; else -> "$ms ms" }
                    val isFreeSelected = freeSelected?.name == s.name && freeSelected?.configText == s.uri
                    val isFreeConnected = isFreeSelected && status.state == VpnState.CONNECTED
                    Card(
                        onClick = {
                            val p = TunnelProfile(id = "free-${s.name}", name = s.name, flag = s.flag, configText = s.uri, createdAt = System.currentTimeMillis())
                            viewModel.connectFree(p)
                        },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isFreeSelected) AppTheme.colors.surfaceVariant else CardBg), border = BorderStroke(1.dp, if (isFreeSelected) Orange.copy(alpha = 0.4f) else Border)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) { ProfileAvatar(s.flag, contentSize = 18.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = AppTheme.colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${com.vpnmobile.app.data.ConfigTools.protocolLabel(s.uri)} · $displayMs", color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (isFreeConnected) Box(Modifier.size(20.dp).clip(CircleShape).background(AppTheme.colors.success.copy(0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), AppTheme.colors.success) }
                            else if (freeLoading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Orange) else Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), Gray)
                        }
                    }
                }
            }
        } else if (tab == "Favorites") {
            val favProfiles = profiles
                .filter { it.id in favorites }
                .filter { query.isBlank() || it.name.contains(query, true) || it.flag.contains(query, true) }
                .filter { countryFilter == null || it.flag == countryFilter }
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
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> AppTheme.colors.success; else -> Color(0xFFFFAB00) }
                        val proto = com.vpnmobile.app.data.ConfigTools.protocolLabel(p.configText)
                        val isSelected = p.id == selected?.id
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) AppTheme.colors.surfaceVariant else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(p.flag, contentSize = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    item {
                        Card(
                            onClick = { viewModel.cleanDeadSubscriptionConfigs() },
                            shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Delete, null, Modifier.size(15.dp), Gray)
                                Spacer(Modifier.width(5.dp))
                                Text("Clean dead configs", color = Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Card(
                            onClick = { viewModel.checkSubscriptionHealth() },
                            shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (healthChecking) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = AppTheme.colors.success)
                                else Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp), AppTheme.colors.success)
                                Spacer(Modifier.width(5.dp))
                                Text(if (healthChecking) "Checking health..." else "Health check", color = AppTheme.colors.success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(subscriptions.filter { query.isBlank() || it.name.contains(query, true) || it.url.contains(query, true) }, key = { it.id }) { sub ->
                        val count = profiles.count { it.subscriptionId == sub.id }
                        val isRefreshing = sub.id in subscriptionRefreshing
                        Card(
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sub.name, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    val health = subHealth[sub.id]
                                    val healthText = if (health != null) " · ${health.healthy}/${health.total} healthy" else ""
                                    Text("$count configs · ${updatedLabel(sub.lastUpdated)}$healthText", color = if (health != null && health.healthy < health.total) Color(0xFFFFAB00) else Gray, fontSize = 11.sp)
                                    Text(subscriptionHost(sub.url), color = AppTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
                                    sub.usageOrNull()?.let { usage ->
                                        Spacer(Modifier.height(8.dp))
                                        SubscriptionUsageBlock(usage)
                                    }
                                }
                                if (isRefreshing) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange)
                                } else {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp).padding(horizontal = 6.dp).clickable { viewModel.refreshOne(sub) }, Orange)
                                }
                                Icon(Icons.Filled.Edit, null, tint = Gray, modifier = Modifier.size(18.dp).padding(horizontal = 6.dp).clickable { renameSub = sub; renameName = sub.name })
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
                val filteredProfiles = profiles
                    .filter { query.isBlank() || it.name.contains(query, true) || it.flag.contains(query, true) }
                    .filter { countryFilter == null || it.flag == countryFilter }
                    .filter { subFilter == null || it.subscriptionId == subFilter }
                val sortedProfiles = if (sortByPing) {
                    filteredProfiles.sortedBy { pingResults[it.id]?.takeIf { r -> r.success }?.ms ?: Int.MAX_VALUE }
                } else {
                    filteredProfiles.sortedByDescending { it.createdAt }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (subscriptions.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = subFilter == null,
                                    onClick = { subFilter = null },
                                    label = { Text("All", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Orange.copy(alpha = 0.18f),
                                        selectedLabelColor = Orange,
                                        labelColor = Gray,
                                    ),
                                )
                                subscriptions.forEach { sub ->
                                    FilterChip(
                                        selected = subFilter == sub.id,
                                        onClick = { subFilter = if (subFilter == sub.id) null else sub.id },
                                        label = { Text(sub.name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Orange.copy(alpha = 0.18f),
                                            selectedLabelColor = Orange,
                                            labelColor = Gray,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            onClick = {
                                if (!findingFastest) {
                                    viewModel.findFastest { best -> if (best != null) onConnect(best) }
                                }
                            },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceVariant), border = BorderStroke(1.dp, Orange.copy(alpha = 0.35f))
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Orange.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp), Orange) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Fastest server", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (findingFastest) "Testing latency..." else "Ping all and connect to the best", color = Gray, fontSize = 11.sp)
                                }
                                if (findingFastest) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange) else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), Orange)
                            }
                        }
                    }
                    items(sortedProfiles, key = { it.id }) { p ->
                        val ping = pingResults[p.id]
                        val pingText = when { ping == null -> "Tap ping"; !ping.success -> "Failed"; else -> "${ping.ms} ms" }
                        val pingColor = when { ping == null || !ping.success -> Gray; ping.ms < 300 -> AppTheme.colors.success; else -> Color(0xFFFFAB00) }
                        val proto = com.vpnmobile.app.data.ConfigTools.protocolLabel(p.configText)
                        val isSelected = p.id == selected?.id
                        val isFav = p.id in favorites
                        Card(
                            onClick = { viewModel.selectProfile(p); onConnect(p) },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) AppTheme.colors.surfaceVariant else CardBg), border = BorderStroke(1.dp, if (isSelected) Orange.copy(alpha = 0.4f) else Border)
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(p.flag, contentSize = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
        QrShareDialog(profile = profile, onDismiss = { qrProfile = null }, hideKeys = hideKeys)
    }

    renameSub?.let { sub ->
        AlertDialog(
            onDismissRequest = { renameSub = null },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Rename subscription", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.trim().isNotBlank(),
                    onClick = {
                        viewModel.renameSubscription(sub, renameName)
                        renameSub = null
                    },
                ) { Text("Save", color = Orange, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renameSub = null }) { Text("Cancel", color = Gray) }
            },
        )
    }

    if (showAddSub) {
        AlertDialog(
            onDismissRequest = { showAddSub = false },
            containerColor = AppTheme.colors.surface,
            title = { Text("Add subscription", color = AppTheme.colors.text, fontWeight = FontWeight.Bold) },
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

@Composable
private fun SubscriptionUsageBlock(usage: SubscriptionUsage) {
    val now = System.currentTimeMillis()
    val expired = usage.isExpired(now)
    val daysLeft = usage.daysLeft(now)
    val low = usage.isLowData()
    val expiring = usage.isExpiringSoon(now)
    val warn = expired || low || expiring
    val tint = when {
        expired || low -> Color(0xFFFF5C5C)
        expiring -> Color(0xFFFFAB00)
        else -> Gray
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatBytes(usage.used), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (usage.hasQuota) {
                Text(" / ${formatBytes(usage.total)}", color = Gray, fontSize = 11.sp)
            } else {
                Text(" used", color = Gray, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            when {
                expired -> Text("Expired", color = Color(0xFFFF5C5C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                daysLeft >= 0L -> Text(
                    if (daysLeft <= 0L) "Expires today" else "$daysLeft days left",
                    color = if (daysLeft <= 3L) Color(0xFFFFAB00) else Gray,
                    fontSize = 11.sp,
                )
            }
        }
        if (usage.hasQuota) {
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { usage.fractionUsed() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (warn) tint else Orange,
                trackColor = Border,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
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
