package com.vpnmobile.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.theme.Cyan
import com.vpnmobile.app.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String, val isSystem: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val splitEnabled by viewModel.splitEnabled.collectAsStateWithLifecycle()
    val splitMode by viewModel.splitMode.collectAsStateWithLifecycle()
    val excludedApps by viewModel.excludedApps.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null || !it.isSystemApp() }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), it.isSystemApp()) }
                .sortedBy { it.label.lowercase() }
            apps = list
            loading = false
        }
    }

    val filtered = remember(apps, query, showSystem, excludedApps) {
        apps.filter {
            (showSystem || !it.isSystem) &&
            (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    }

    Scaffold(
        containerColor = Color(0xFF070E1F),
        topBar = {
            TopAppBar(
                title = { Text("Split Tunnel", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF070E1F)),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121D36)), border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Split Tunnel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(if (splitEnabled) "Enabled • ${excludedApps.size} apps" else "Route all apps through VPN", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2))
                    }
                    Switch(checked = splitEnabled, onCheckedChange = viewModel::setSplitEnabled, colors = SwitchDefaults.colors(checkedTrackColor = Cyan, checkedThumbColor = Color.White))
                }
                if (splitEnabled) {
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = splitMode == "exclude", onClick = { viewModel.setSplitMode("exclude") }, label = { Text("Exclude selected", fontSize = 12.sp) }, leadingIcon = if (splitMode == "exclude") {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan.copy(0.18f), selectedLabelColor = Cyan))
                        FilterChip(selected = splitMode == "include", onClick = { viewModel.setSplitMode("include") }, label = { Text("Only selected", fontSize = 12.sp) }, leadingIcon = if (splitMode == "include") {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan.copy(0.18f), selectedLabelColor = Cyan))
                    }
                    Text(
                        text = if (splitMode == "exclude") "Selected apps will BYPASS VPN (direct internet)" else "Only selected apps will USE VPN",
                        style = MaterialTheme.typography.labelSmall, color = Success, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Success.copy(0.10f)).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (splitEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search apps...", color = Color(0xFF5B6B8A)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFF8EA0C2)) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF1E2D4A), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Cyan, focusedContainerColor = Color(0xFF121D36), unfocusedContainerColor = Color(0xFF121D36)),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} apps", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2), modifier = Modifier.weight(1f))
                    Text("Show system", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2))
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it }, modifier = Modifier.size(width = 44.dp, height = 26.dp))
                }
                Spacer(Modifier.height(8.dp))
                if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val selected = app.packageName in excludedApps
                        Card(
                            onClick = { scope.launch { viewModel.toggleSplitApp(app.packageName) } },
                            shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF162040) else Color(0xFF121D36)),
                            border = if (selected) BorderStroke(1.dp, Cyan.copy(0.4f)) else BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(packageName = app.packageName)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5B6B8A), maxLines = 1)
                                }
                                Checkbox(checked = selected, onCheckedChange = { scope.launch { viewModel.toggleSplitApp(app.packageName) } }, colors = CheckboxDefaults.colors(checkedColor = Cyan, uncheckedColor = Color(0xFF3E4F6A)))
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF121D36)).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF1A2B4A)), contentAlignment = Alignment.Center) { Text("◈", color = Cyan, fontSize = 20.sp) }
                        Spacer(Modifier.height(12.dp))
                        Text("Split tunneling disabled", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("All app traffic goes through VPN", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8EA0C2))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48).asImageBitmap() } catch (_: Exception) { null }
    }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A2B4A)), contentAlignment = Alignment.Center) { Text(packageName.take(1).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

private fun ApplicationInfo.isSystemApp(): Boolean = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
