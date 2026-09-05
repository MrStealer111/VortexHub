package com.vpnmobile.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnmobile.app.data.WarpGenerator
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.theme.Cyan
import com.vpnmobile.app.ui.theme.Success
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpGenerateScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var selectedIp by remember { mutableStateOf("162.159.192.1") }
    var selectedPort by remember { mutableStateOf(2408) }
    var expanded by remember { mutableStateOf(false) }
    var portExpanded by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WarpGenerator.WarpResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val infinite = rememberInfiniteTransition(label = "warp")
    val pulse by infinite.animateFloat(initialValue = 0.97f, targetValue = 1.03f, animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    val ips = listOf("162.159.192.1", "162.159.193.1", "188.114.96.1", "188.114.97.1")
    val ports = listOf(500, 1701, 2408, 4500)

    Scaffold(
        containerColor = Color(0xFF070E1F),
        topBar = {
            TopAppBar(
                title = { Text("WARP Auto", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF070E1F)),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF0F1B33), Color(0xFF162040)))).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Color(0xFF3B82F6)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(22.dp), Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cloudflare WARP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Key မထည့်ပဲ Auto WireGuard config", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2))
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121D36)), border = BorderStroke(1.dp, Color(0xFF1E2D4A))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Endpoint", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8EA0C2), fontWeight = FontWeight.SemiBold)
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = selectedIp, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF24324F), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ips.forEach { ip -> DropdownMenuItem(text = { Text(ip) }, onClick = { selectedIp = ip; expanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = portExpanded, onExpandedChange = { portExpanded = !portExpanded }) {
                        OutlinedTextField(value = selectedPort.toString(), onValueChange = {}, readOnly = true, label = { Text("Port", color = Color(0xFF8EA0C2)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(portExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF24324F), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        ExposedDropdownMenu(expanded = portExpanded, onDismissRequest = { portExpanded = false }) {
                            ports.forEach { p -> DropdownMenuItem(text = { Text(p.toString()) }, onClick = { selectedPort = p; portExpanded = false }) }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (generating) return@Button
                    generating = true; error = null; result = null
                    scope.launch {
                        try {
                            val r = WarpGenerator.generate(selectedIp, selectedPort, autoBest = false)
                            result = r
                        } catch (e: Exception) {
                            error = e.message ?: "Failed"
                        }
                        generating = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).scale(pulse),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                enabled = !generating,
            ) {
                if (generating) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF04242E), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Bolt, null, Modifier.size(18.dp), Color(0xFF04242E))
                Spacer(Modifier.width(8.dp))
                Text(if (generating) "Generating..." else "Generate WARP Config", color = Color(0xFF04242E), fontWeight = FontWeight.Black, fontSize = 15.sp)
            }

            error?.let {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1E)), border = BorderStroke(1.dp, Color(0xFFF87171).copy(0.3f))) {
                    Text(it, Modifier.padding(14.dp), color = Color(0xFFFECACA), style = MaterialTheme.typography.bodySmall)
                }
            }

            result?.let { r ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1F2E)), border = BorderStroke(1.dp, Success.copy(0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), Success)
                            Text("Generated!", color = Success, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(r.endpoint, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2))
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF070E1F)).padding(12.dp)) {
                            Text(r.config, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = Color(0xFFE8EEF9))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { clipboard.setText(AnnotatedString(r.config)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D4A))) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(14.dp), Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Copy", color = Color.White)
                            }
                            Button(onClick = {
                                viewModel.addProfile("WARP ${r.endpoint}", "🌐", r.config)
                                onBack()
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Success)) {
                                Text("Save & Connect", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121D36)), border = BorderStroke(1.dp, Color(0xFF1E2D4A))) {
                Text("warp-conf-gen.vercel.app API ကို အသုံးပြုပြီး Cloudflare မှာ Auto register လုပ်ပြီး WireGuard config ထုတ်ပေးတာပါ။ Key ကို device ထဲမှာပဲ generate လုပ်ပါတယ်။", Modifier.padding(14.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2), lineHeight = 16.sp)
            }
        }
    }
}
