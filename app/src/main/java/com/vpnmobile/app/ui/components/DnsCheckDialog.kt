package com.vpnmobile.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vpnmobile.app.data.DnsCheck

@Composable
fun DnsCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<DnsCheck.Result?>(null) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        result = DnsCheck.run(context)
        running = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(AppTheme.colors.surface, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            Text("Connection & DNS check", color = AppTheme.colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppTheme.colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking...", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                }
            } else {
                val r = result
                ResultRow("Public IP", r?.egressIp ?: "Unavailable")
                ResultRow("DNS resolver seen", r?.resolverIp ?: "Unavailable")
                ResultRow("System DNS", r?.dnsServers?.ifBlank { "Automatic" } ?: "Unavailable")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Resolver shows the DNS server this device currently uses. " +
                        "For encrypted DNS, enable Private DNS in system settings.",
                    color = AppTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss) { Text("Close", color = AppTheme.colors.accent) }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(130.dp))
        Text(value, color = AppTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
