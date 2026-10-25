package com.vpnmobile.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vpnmobile.app.R
import com.vpnmobile.app.VpnApplication
import com.vpnmobile.app.vpn.VpnState
import com.vpnmobile.app.vpn.VpnToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, currentState(context)) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.vpnmobile.app.action.WIDGET_TOGGLE"

        fun updateAll(context: Context, state: VpnState? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            val resolved = state ?: currentState(context)
            ids.forEach { updateWidget(context, manager, it, resolved) }
        }

        fun currentState(context: Context): VpnState = try {
            (context.applicationContext as VpnApplication).container.vpn.status.value.state
        } catch (_: Exception) {
            VpnState.DISCONNECTED
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, state: VpnState) {
            val views = RemoteViews(context.packageName, R.layout.vpn_widget)
            views.setTextViewText(
                R.id.widget_status,
                when (state) {
                    VpnState.CONNECTED -> "Connected"
                    VpnState.CONNECTING -> "Connecting..."
                    VpnState.BLOCKED -> "Blocked"
                    VpnState.DISCONNECTED -> "Disconnected"
                },
            )
            views.setTextViewText(
                R.id.widget_button,
                if (state == VpnState.DISCONNECTED) "Connect" else "Disconnect",
            )
            val intent = Intent(context, VpnWidgetActionReceiver::class.java).setAction(ACTION_TOGGLE)
            val pending = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_button, pending)
            manager.updateAppWidget(id, views)
        }
    }
}

class VpnWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VpnWidgetProvider.ACTION_TOGGLE) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            when (VpnToggle.toggle(context)) {
                is VpnToggle.Result.NeedsPermission, is VpnToggle.Result.NoProfile ->
                    withContext(Dispatchers.Main) { VpnToggle.openApp(context) }
                else -> Unit
            }
            VpnWidgetProvider.updateAll(context, VpnWidgetProvider.currentState(context))
            pending.finish()
        }
    }
}
