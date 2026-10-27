package com.vpnmobile.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.ShadowsocksUri
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.effects.GlassPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onConnect: (TunnelProfile) -> Unit,
    onWarpGenerate: (() -> Unit)? = null,
) {
    var protocol by remember { mutableStateOf("WireGuard") }
    var name by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(v: String): String? {
        val t = v.trim()
        if (t.isBlank()) return null
        return when (protocol) {
            "WARP" -> if (!t.startsWith("warp://")) "WARP config must start with warp://" else null
            "WireGuard" -> if (ConfigTools.normalizeWireGuard(t) == null) "Invalid WireGuard config — paste [Interface] text or a base64 wireguard:// link" else null
            "Shadowsocks" -> if (ShadowsocksUri.parse(t) == null) "Invalid Shadowsocks link — expected ss://method:password@host:port" else null
            else -> null
        }
    }

    val canSave = name.isNotBlank() && uri.isNotBlank() && validate(uri) == null

    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Add Server", fontWeight = FontWeight.Bold, color = AppTheme.colors.text) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppTheme.colors.text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.bg),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface), border = BorderStroke(1.dp, AppTheme.colors.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Protocol", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("WireGuard", "WARP", "Shadowsocks").forEach { p ->
                            FilterChip(
                                selected = protocol == p, onClick = { protocol = p; error = validate(uri) },
                                label = { Text(p, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                leadingIcon = if (protocol == p) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppTheme.colors.accent, selectedLabelColor = Color(0xFFFFFFFF), containerColor = AppTheme.colors.surfaceVariant, labelColor = AppTheme.colors.textSecondary),
                                border = BorderStroke(1.dp, if (protocol == p) AppTheme.colors.accent else AppTheme.colors.border),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Name", color = AppTheme.colors.textSecondary) },
                placeholder = {
                    Text(
                        when (protocol) {
                            "Shadowsocks" -> "e.g. Outline Server"
                            "WARP" -> "e.g. WARP"
                            else -> "e.g. My WireGuard"
                        },
                        color = AppTheme.colors.textSecondary.copy(alpha = 0.7f),
                    )
                },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.accent, unfocusedBorderColor = AppTheme.colors.border, focusedTextColor = AppTheme.colors.text, unfocusedTextColor = AppTheme.colors.text, cursorColor = AppTheme.colors.accent, focusedContainerColor = AppTheme.colors.surface, unfocusedContainerColor = AppTheme.colors.surface),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = uri, onValueChange = { uri = it; error = validate(it) }, modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        when (protocol) {
                            "WARP" -> "WARP Config"
                            "Shadowsocks" -> "Shadowsocks Link"
                            else -> "WireGuard Config"
                        },
                        color = AppTheme.colors.textSecondary,
                    )
                },
                placeholder = {
                    Text(
                        when (protocol) {
                            "WARP" -> "warp://auto"
                            "Shadowsocks" -> "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNzd29yZA@host:port/?outline=1"
                            else -> "[Interface]\nPrivateKey = ...\nAddress = ...\n[Peer]\nPublicKey = ...\nEndpoint = host:port"
                        },
                        color = AppTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                },
                minLines = 4, maxLines = 8, isError = error != null, supportingText = error?.let {{ Text(it, color = AppTheme.colors.error) }},
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.text),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppTheme.colors.accent, unfocusedBorderColor = AppTheme.colors.border, focusedContainerColor = AppTheme.colors.surface, unfocusedContainerColor = AppTheme.colors.surface),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (protocol) {
                    "WARP" -> "Paste warp://auto to use Cloudflare WARP with an automatic endpoint."
                    "Shadowsocks" -> "Paste your Shadowsocks link (ss://...) or scan your Outline access key QR code."
                    else -> "Paste your WireGuard config (the [Interface] / [Peer] text) or a wireguard:// link."
                },
                style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary.copy(alpha = 0.7f), lineHeight = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            GlassPrimaryButton(
                onClick = {
                    val flag = when (protocol) {
                        "WARP" -> "🌐"
                        "Shadowsocks" -> "🔒"
                        else -> "⚡"
                    }
                    val configText = if (protocol == "WireGuard") {
                        ConfigTools.normalizeWireGuard(uri.trim()) ?: uri.trim()
                    } else {
                        uri.trim()
                    }
                    viewModel.addProfile(name.trim(), flag, configText) { profile ->
                        onConnect(profile)
                        onBack()
                    }
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save & Connect", color = AppTheme.colors.text, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            Text("This build supports WireGuard, Shadowsocks and Cloudflare WARP.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4A4A4C), modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
