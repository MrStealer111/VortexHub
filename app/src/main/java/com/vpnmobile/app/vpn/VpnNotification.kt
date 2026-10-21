package com.vpnmobile.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vpnmobile.app.MainActivity
import com.vpnmobile.app.R

object VpnNotification {
    private const val CHANNEL_ID = "vpn_status"
    private const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows VPN connection status and data usage"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    fun show(context: Context, profileName: String, rx: Long, tx: Long, isConnected: Boolean) {
        ensureChannel(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (isConnected) "VPN Connected • $profileName" else "VPN Connecting..."
        val text = if (isConnected) "↓ ${format(rx)}  ↑ ${format(tx)}  • Tap to manage" else "Establishing secure tunnel..."
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(isConnected)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()
        mgr.notify(NOTIF_ID, notif)
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    private fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
    }
}
