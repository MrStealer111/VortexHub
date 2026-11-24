package com.vpnmobile.app.ui.screens

import android.annotation.SuppressLint
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnmobile.app.ui.components.glass
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vpnmobile.app.ui.MainViewModel

private val BgBlack: Color
    @Composable get() = AppTheme.colors.bg
private val CardBg: Color
    @Composable get() = AppTheme.colors.surface
private val Gray: Color
    @Composable get() = AppTheme.colors.textSecondary
private val Border: Color
    @Composable get() = AppTheme.colors.border

@SuppressLint("HardwareIds")
@Composable
fun PersonScreen(viewModel: MainViewModel, onPremium: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown" }
    val green = Color(0xFF1DB954)

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(AppTheme.colors.accent.copy(0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = AppTheme.colors.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VortexHub", color = AppTheme.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Your private profile", color = Gray, fontSize = 12.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.surface).padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text("ID ${deviceId.take(6)}", color = AppTheme.colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.glass(RoundedCornerShape(20.dp))
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(74.dp).clip(CircleShape).background(green.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = green, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Protected", color = green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your traffic is routed through a secure tunnel",
                    color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.glass(RoundedCornerShape(16.dp))
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device ID", color = AppTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(deviceId, color = Gray, fontSize = 11.sp, maxLines = 1)
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(deviceId)) },
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colors.accent.copy(0.12f))
                ) {
                    Icon(Icons.Filled.ContentCopy, null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), selected = true).clickable { onPremium?.invoke() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VortexHub Premium", color = AppTheme.colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Unlock premium servers", color = AppTheme.colors.accent, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Upgrade", color = AppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(15.dp), AppTheme.colors.accent)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable { onSettings?.invoke() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Settings", color = AppTheme.colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("VPN, connection, appearance", color = Gray, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), Gray)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "VortexHub · secure by design",
            color = Gray, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
