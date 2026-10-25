package com.vpnmobile.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.util.QrCode
import com.vpnmobile.app.util.ShareUtils
import java.io.File
import java.io.FileOutputStream

@Composable
fun QrShareDialog(profile: TunnelProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val bitmap: Bitmap? = remember(profile.id, profile.configText) {
        runCatching { QrCode.generate(profile.configText) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(profile.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Scan or share this config", color = Color(0xFF8E8E93), fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            if (bitmap != null) {
                Box(Modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Config QR code", modifier = Modifier.size(240.dp))
                }
            } else {
                Text(
                    "This config is too large for a QR code.\nUse Copy or Share instead.",
                    color = Color(0xFFF87171), fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(profile.configText))
                }) { Text("Copy", color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold) }
                TextButton(onClick = {
                    ShareUtils.shareText(context, profile.configText, profile.name)
                }) { Text("Share link", color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold) }
                TextButton(enabled = bitmap != null, onClick = {
                    val bmp = bitmap ?: return@TextButton
                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                    val file = File(dir, "qr-${profile.id.take(8)}.png")
                    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ShareUtils.shareFile(context, file, "image/png", profile.name)
                }) { Text("Share QR", color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold) }
            }
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF8E8E93)) }
        }
    }
}
