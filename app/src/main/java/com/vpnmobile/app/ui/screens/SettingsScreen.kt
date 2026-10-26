package com.vpnmobile.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.BuildConfig
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.components.DnsCheckDialog
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import com.vpnmobile.app.util.Diagnostics
import com.vpnmobile.app.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgBlack: Color
    @Composable get() = AppTheme.colors.bg
private val CardBg: Color
    @Composable get() = AppTheme.colors.surface
private val Orange: Color
    @Composable get() = AppTheme.colors.accent
private val Gray: Color
    @Composable get() = AppTheme.colors.textSecondary
private val Hairline: Color
    @Composable get() = AppTheme.colors.border

private enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    APPEARANCE("Appearance", "Theme, accent color & language", Icons.Rounded.Palette),
    VPN("VPN", "Connection, routing & performance", Icons.Rounded.Security),
    DNS("DNS", "Custom DNS, DoH & connection check", Icons.Rounded.Dns),
    SECURITY("Security", "App lock, screenshots & keys", Icons.Rounded.Lock),
    DIAGNOSTICS("Diagnostics", "Export redacted logs", Icons.Rounded.BugReport),
    ABOUT("About", "Version & feature overview", Icons.Rounded.Info),
}

private val accentOptions = listOf(
    Triple("orange", "Orange", Color(0xFFFF7A00)),
    Triple("green", "Green", Color(0xFF22C55E)),
    Triple("blue", "Blue", Color(0xFF3B82F6)),
)

private fun themeLabel(value: String) = when (value) {
    "dark" -> "Dark"
    "light" -> "Light"
    else -> "System"
}

private fun languageLabel(value: String) = when (value) {
    "en" -> "English"
    "my" -> "မြန်မာ"
    else -> "System"
}

private fun accentLabel(value: String) = when (value) {
    "green" -> "Green"
    "blue" -> "Blue"
    else -> "Orange"
}

private fun openSystemSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "Settings screen not available", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSplitTunnel: () -> Unit = {},
) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()

    BackHandler(enabled = section != null) { section = null }

    AnimatedContent(
        targetState = section,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "settings-nav",
    ) { current ->
        if (current == null) {
            SettingsHub(
                themeSummary = "${themeLabel(themeMode)} · ${accentLabel(accentColor)} · ${languageLabel(language)}",
                onOpen = { section = it },
                onBack = onBack,
            )
        } else {
            when (current) {
                SettingsSection.APPEARANCE -> AppearanceSection(viewModel, { section = null })
                SettingsSection.VPN -> VpnSection(viewModel, onOpenSplitTunnel, { section = null })
                SettingsSection.DNS -> DnsSection(viewModel, { section = null })
                SettingsSection.SECURITY -> SecuritySection(viewModel, { section = null })
                SettingsSection.DIAGNOSTICS -> DiagnosticsSection({ section = null })
                SettingsSection.ABOUT -> AboutSection({ section = null })
            }
        }
    }
}

@Composable
private fun SettingsHub(
    themeSummary: String,
    onOpen: (SettingsSection) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeaderBar(title = "Settings", onBack = onBack)
        Text(
            "Manage your app",
            color = Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp, bottom = 14.dp),
        )

        SettingsSection.entries.forEach { entry ->
            HubCard(
                icon = entry.icon,
                title = entry.title,
                subtitle = if (entry == SettingsSection.APPEARANCE) themeSummary else entry.subtitle,
                onClick = { onOpen(entry) },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "VortexHub · v${BuildConfig.VERSION_NAME}",
            color = Gray.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Orange.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(22.dp), Orange)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), Gray)
    }
}

@Composable
private fun SectionHeaderBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(22.dp), Orange)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, color = AppTheme.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(BgBlack)) {
        SectionHeaderBar(title = title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            content = content,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
private fun CardDivider() {
    Box(Modifier.padding(start = 16.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colors.text,
                checkedTrackColor = Orange,
                uncheckedThumbColor = AppTheme.colors.text,
                uncheckedTrackColor = Hairline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(action, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), Orange)
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Orange.copy(alpha = 0.18f),
                    selectedLabelColor = Orange,
                    labelColor = Gray,
                ),
            )
        }
    }
}

@Composable
private fun AppearanceSection(viewModel: MainViewModel, onBack: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()

    SectionScaffold(title = "Appearance", onBack = onBack) {
        GroupLabel("Theme")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("Color mode", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Match the system or pick a mode", color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = listOf("system" to "System", "dark" to "Dark", "light" to "Light"),
                    selected = themeMode,
                    onSelect = { viewModel.setThemeMode(it) },
                )
            }
        }

        GroupLabel("Accent color")
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                accentOptions.forEach { (value, label, color) ->
                    AccentSwatch(
                        label = label,
                        color = color,
                        selected = accentColor == value,
                        onClick = { viewModel.setAccentColor(value) },
                    )
                }
            }
        }

        GroupLabel("Language")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("App language", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Follow the system or choose manually", color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = listOf("system" to "System", "en" to "English", "my" to "မြန်မာ"),
                    selected = language,
                    onSelect = { viewModel.setLanguage(it) },
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) AppTheme.colors.text else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) AppTheme.colors.text else Gray, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun VpnSection(viewModel: MainViewModel, onOpenSplitTunnel: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val killSwitch by viewModel.killSwitch.collectAsStateWithLifecycle()
    val splitEnabled by viewModel.splitEnabled.collectAsStateWithLifecycle()
    val blockIpv6 by viewModel.blockIpv6.collectAsStateWithLifecycle()
    val autoSortByLatency by viewModel.autoSortByLatency.collectAsStateWithLifecycle()
    val autoRefreshInterval by viewModel.autoRefreshInterval.collectAsStateWithLifecycle()

    SectionScaffold(title = "VPN", onBack = onBack) {
        GroupLabel("Connection")
        SettingsCard {
            ToggleRow("Auto Connect", "Connect VPN on app start", autoConnect) { viewModel.setAutoConnect(it) }
            CardDivider()
            ToggleRow("Auto Reconnect", "Reconnect when VPN drops", autoReconnect) { viewModel.setAutoReconnect(it) }
            CardDivider()
            ToggleRow("Kill Switch", "Block internet if the tunnel drops", killSwitch) { viewModel.setKillSwitch(it) }
        }

        GroupLabel("System")
        SettingsCard {
            ActionRow("Always-on VPN", "Enable Always-on + block without VPN", "Open") {
                openSystemSettings(context, Settings.ACTION_VPN_SETTINGS)
            }
            CardDivider()
            ActionRow("Battery optimization", "Stay connected in the background", "Open") {
                openSystemSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        }

        GroupLabel("Routing")
        SettingsCard {
            ToggleRow("Split Tunnel", "Route only selected apps through VPN", splitEnabled) { viewModel.setSplitEnabled(it) }
            CardDivider()
            ActionRow("Split Tunnel apps", "Choose apps to include or exclude", "Manage") { onOpenSplitTunnel() }
        }

        GroupLabel("Performance")
        SettingsCard {
            ToggleRow("Block IPv6", "Prevent IPv6 leaks", blockIpv6) { viewModel.setBlockIpv6(it) }
            CardDivider()
            ToggleRow("Auto-sort by latency", "Sort servers by lowest ping", autoSortByLatency) { viewModel.setAutoSortByLatency(it) }
            CardDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Auto-refresh subscriptions", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Update configs in the background", color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = listOf("off" to "Off", "6" to "6h", "12" to "12h", "24" to "24h"),
                    selected = autoRefreshInterval,
                    onSelect = { viewModel.setAutoRefreshInterval(it) },
                )
            }
        }
    }
}

@Composable
private fun DnsSection(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val customDns by viewModel.customDns.collectAsStateWithLifecycle()
    var showDnsCheck by remember { mutableStateOf(false) }

    SectionScaffold(title = "DNS", onBack = onBack) {
        GroupLabel("Resolver")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("Custom DNS", color = AppTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(customDns, color = Gray, fontSize = 12.sp)
            }
            CardDivider()
            ActionRow("Private DNS (DoH)", "Encrypt DNS system-wide (e.g. one.one.one.one)", "Open") {
                if (Build.VERSION.SDK_INT >= 28) openSystemSettings(context, "android.settings.PRIVATE_DNS_SETTINGS")
                else openSystemSettings(context, Settings.ACTION_WIRELESS_SETTINGS)
            }
        }

        GroupLabel("Check")
        SettingsCard {
            ActionRow("Connection & DNS check", "Show public IP and the DNS resolver in use", "Run") {
                showDnsCheck = true
            }
        }
    }

    if (showDnsCheck) {
        DnsCheckDialog(onDismiss = { showDnsCheck = false })
    }
}

@Composable
private fun SecuritySection(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val biometricLock by viewModel.biometricLock.collectAsStateWithLifecycle()
    val secureScreen by viewModel.secureScreen.collectAsStateWithLifecycle()
    val hideKeys by viewModel.hideKeys.collectAsStateWithLifecycle()
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    SectionScaffold(title = "Security", onBack = onBack) {
        GroupLabel("Protection")
        SettingsCard {
            ToggleRow(
                "App lock",
                if (biometricAvailable) "Require biometrics to open VortexHub" else "Biometric unlock is not available on this device",
                biometricLock,
            ) {
                if (it && !biometricAvailable) {
                    Toast.makeText(context, "Set up a fingerprint or face unlock first", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setBiometricLock(it)
                }
            }
            CardDivider()
            ToggleRow("Block screenshots", "Hide app content in screenshots and Recents", secureScreen) { viewModel.setSecureScreen(it) }
            CardDivider()
            ToggleRow("Hide keys by default", "Mask configs and QR codes until revealed", hideKeys) { viewModel.setHideKeys(it) }
        }
    }
}

@Composable
private fun DiagnosticsSection(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SectionScaffold(title = "Diagnostics", onBack = onBack) {
        GroupLabel("Logs")
        SettingsCard {
            ActionRow("Export diagnostics", "Redacted connection log for troubleshooting", "Export") {
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { Diagnostics.export(context) }
                        ShareUtils.shareFile(context, file, "text/plain", "VortexHub diagnostics")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(onBack: () -> Unit) {
    SectionScaffold(title = "About", onBack = onBack) {
        GroupLabel("Application")
        SettingsCard {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Orange.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Security, null, Modifier.size(24.dp), Orange)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("VortexHub VPN", color = AppTheme.colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Version ${BuildConfig.VERSION_NAME}", color = Gray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                listOf(
                    "WireGuard / Shadowsocks / WARP",
                    "Subscriptions, QR export, fastest server",
                    "Split tunneling + kill switch",
                    "Auto-reconnect + diagnostics",
                    "Server favorites & history",
                ).forEach { feature ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), Orange)
                        Spacer(Modifier.width(10.dp))
                        Text(feature, color = Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
