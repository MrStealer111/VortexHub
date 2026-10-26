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
    private const val ALERT_CHANNEL_ID = "vpn_alerts"
    private const val SUB_CHANNEL_ID = "sub_updates"
    const val NOTIF_ID = 1001
    const val ALERT_NOTIF_ID = 1002
    const val SUB_NOTIF_ID = 1003
    const val SUB_ALERT_NOTIF_ID = 1004

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
            if (mgr.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, "VPN Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Alerts when the VPN tunnel drops or kill switch activates"
                        setShowBadge(true)
                    }
                )
            }
            if (mgr.getNotificationChannel(SUB_CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(SUB_CHANNEL_ID, "Subscription Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "New configs, low data and expiry alerts for subscriptions"
                        setShowBadge(true)
                    }
                )
            }
        }
    }

    fun build(
        context: Context,
        profileName: String,
        rx: Long,
        tx: Long,
        isConnected: Boolean,
        blocked: Boolean = false,
    ): android.app.Notification {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            blocked -> "VPN Blocked • Kill Switch"
            isConnected -> "VPN Connected • $profileName"
            else -> "VPN Connecting..."
        }
        val text = when {
            blocked -> "Tunnel lost — all traffic is blocked. Tap to manage."
            isConnected -> "↓ ${format(rx)}  ↑ ${format(tx)}  • Tap to manage"
            else -> "Establishing secure tunnel..."
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(isConnected || blocked)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)

        // Quick action: toggle connection
        val toggleIntent = Intent(context, VpnNotificationActionReceiver::class.java).apply {
            action = VpnNotificationActionReceiver.ACTION_TOGGLE
        }
        val togglePi = PendingIntent.getBroadcast(
            context, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            R.mipmap.ic_launcher,
            if (isConnected) "Disconnect" else "Connect",
            togglePi,
        )

        if (blocked) {
            val reconnectIntent = Intent(context, VpnNotificationActionReceiver::class.java).apply {
                action = VpnNotificationActionReceiver.ACTION_DISCONNECT
            }
            val reconnectPi = PendingIntent.getBroadcast(
                context, 2, reconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.mipmap.ic_launcher, "Dismiss", reconnectPi)
        }
        return builder.build()
    }

    fun show(
        context: Context,
        profileName: String,
        rx: Long,
        tx: Long,
        isConnected: Boolean,
        blocked: Boolean = false,
    ) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, build(context, profileName, rx, tx, isConnected, blocked))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    fun showKillSwitchAlert(context: Context, profileName: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kill Switch Active")
            .setContentText("Tunnel to $profileName lost — all traffic blocked.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ALERT_NOTIF_ID, notif)
    }

    fun showSubscriptionUpdate(context: Context, subName: String, newCount: Int) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, SUB_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New servers in $subName")
            .setContentText("$newCount new config(s) added to your subscription.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(SUB_NOTIF_ID, notif)
    }

    fun showSubscriptionAlert(context: Context, title: String, message: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, SUB_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(SUB_ALERT_NOTIF_ID, notif)
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
