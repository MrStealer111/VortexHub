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
import androidx.compose.material3.Text
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
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.effects.GlassPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(viewModel: MainViewModel, onBack: () -> Unit, onWarpGenerate: (() -> Unit)? = null) {
    var protocol by remember { mutableStateOf("WireGuard") }
    var name by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(v: String): String? {
        val t = v.trim()
        if (t.isBlank()) return null
        return when (protocol) {
            "WARP" -> if (!t.startsWith("warp://")) "WARP config must start with warp://" else null
            "WireGuard" -> if (!t.startsWith("wireguard://") && !t.contains("[Interface]")) "WireGuard config must contain [Interface] or start with wireguard://" else null
            else -> null
        }
    }

    val canSave = name.isNotBlank() && uri.isNotBlank() && validate(uri) == null

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("Add Server", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A)),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), border = BorderStroke(1.dp, Color(0xFF2C2C2E))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Protocol", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("WireGuard", "WARP").forEach { p ->
                            FilterChip(
                                selected = protocol == p, onClick = { protocol = p; error = validate(uri) },
                                label = { Text(p, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                leadingIcon = if (protocol == p) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF8C00), selectedLabelColor = Color(0xFFFFFFFF), containerColor = Color(0xFF2C2C2E), labelColor = Color(0xFF8E8E93)),
                                border = BorderStroke(1.dp, if (protocol == p) Color(0xFFFF8C00) else Color(0xFF3A3A3C)),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Name", color = Color(0xFF8E8E93)) }, placeholder = { Text("e.g. My WireGuard", color = Color(0xFF5C5C5E)) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF8C00), unfocusedBorderColor = Color(0xFF3A3A3C), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFFF8C00), focusedContainerColor = Color(0xFF1C1C1E), unfocusedContainerColor = Color(0xFF1C1C1E)),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = uri, onValueChange = { uri = it; error = validate(it) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (protocol == "WARP") "WARP Config" else "WireGuard Config", color = Color(0xFF8E8E93)) },
                placeholder = { Text(if (protocol == "WARP") "warp://auto" else "[Interface]\nPrivateKey = ...\nAddress = ...\n[Peer]\nPublicKey = ...\nEndpoint = host:port", color = Color(0xFF5C5C5E), fontSize = 12.sp) },
                minLines = 4, maxLines = 8, isError = error != null, supportingText = error?.let {{ Text(it, color = Color(0xFFF87171)) }},
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.White),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF8C00), unfocusedBorderColor = Color(0xFF3A3A3C), focusedContainerColor = Color(0xFF1C1C1E), unfocusedContainerColor = Color(0xFF1C1C1E)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (protocol == "WARP") "Paste warp://auto to use Cloudflare WARP with an automatic endpoint."
                else "Paste your WireGuard config (the [Interface] / [Peer] text) or a wireguard:// link.",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF5C5C5E), lineHeight = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            GlassPrimaryButton(
                onClick = {
                    val flag = if (protocol == "WARP") "🌐" else "⚡"
                    viewModel.addProfile(name.trim(), flag, uri.trim())
                    onBack()
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save & Connect", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            Text("This build supports WireGuard configs and Cloudflare WARP only.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4A4A4C), modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
