package com.vpnmobile.app.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.vpnmobile.app.ui.components.ProfileAvatar
import com.vpnmobile.app.ui.effects.GlassPrimaryButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.provider.Settings
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.ui.MainViewModel

private val BgBlack = Color(0xFF0A0A0A)
private val CardBg = Color(0xFF1E1E1E)
private val Gray = Color(0xFF8E8E93)
private val Border = Color(0xFF2C2C2E)

@SuppressLint("HardwareIds")
@Composable
fun PersonScreen(viewModel: MainViewModel, onPremium: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    val context = LocalContext.current
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown" }
    Column(Modifier.fillMaxSize().background(BgBlack).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("VortexHub", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("My Keys • Update without rebuild", color = Gray, fontSize = 12.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("ID: ${deviceId.take(8)}", color = Color(0xFFFF8C00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), border = BorderStroke(1.dp, Border)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device Hardware ID", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(deviceId, color = Gray, fontSize = 10.sp, maxLines = 1)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, null, tint = Color(0xFFFF8C00), modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)) {
            Column(Modifier.padding(12.dp)) {
                Text("Free Server URL (GitHub/Cloudflare)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://raw.githubusercontent.com/.../server.json", color = Gray, fontSize = 11.sp) },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF8C00), unfocusedBorderColor = Border, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFFF8C00), focusedContainerColor = Color(0xFF121212), unfocusedContainerColor = Color(0xFF121212)),
                )
                Spacer(Modifier.height(8.dp))
                GlassPrimaryButton(onClick = { viewModel.setServerUrl(urlInput) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save & Refresh Free", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Empty = use bundled + Cloudflare. Paste your GitHub raw URL, update JSON there, then Free auto updates, no APK rebuild.", color = Gray, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)) {
            Column(Modifier.padding(12.dp)) {
                Text("Custom DNS Switcher", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val customDns by viewModel.customDns.collectAsStateWithLifecycle()
                    listOf("1.1.1.1, 1.0.0.1" to "Cloudflare", "8.8.8.8, 8.8.4.4" to "Google").forEach { (dns, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = customDns == dns, onClick = { viewModel.setCustomDns(dns) },
                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF8C00), selectedLabelColor = Color.White, containerColor = Color(0xFF121212), labelColor = Gray),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF8C00).copy(0.12f)), border = BorderStroke(1.dp, Color(0xFFFF8C00).copy(0.3f)), modifier = Modifier.fillMaxWidth().clickable { onPremium?.invoke() }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("VortexHub Premium", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Unlock premium servers", color = Color(0xFFFF8C00), fontSize = 11.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Upgrade", color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(14.dp), Color(0xFFFF8C00))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().clickable { onSettings?.invoke() }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("VPN, DNS, Auto-connect", color = Gray, fontSize = 11.sp) }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), Gray)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("My Keys", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("${profiles.size} configs saved (IP hidden)", color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No keys yet\nAdd from the Configs tab", color = Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, Border)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(p.flag, contentSize = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { clipboard.setText(AnnotatedString(p.configText)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, null, tint = Gray, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { viewModel.deleteProfile(p) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp)) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF121212)).padding(8.dp)) {
                                Text(com.vpnmobile.app.data.ConfigTools.endpointPreview(p.configText), color = Gray, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
