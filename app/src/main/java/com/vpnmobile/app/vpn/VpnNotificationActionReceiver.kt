package com.vpnmobile.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.vpnmobile.app.VpnApplication
import com.vpnmobile.app.vpn.VpnState
import com.vpnmobile.app.widget.VpnWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.vpnmobile.app.action.NOTIF_TOGGLE"
        const val ACTION_DISCONNECT = "com.vpnmobile.app.action.NOTIF_DISCONNECT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> handleToggle(context)
            ACTION_DISCONNECT -> handleDisconnect(context)
        }
    }

    private fun handleToggle(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            when (VpnToggle.toggle(context)) {
                is VpnToggle.Result.NeedsPermission -> withContext(Dispatchers.Main) { VpnToggle.openApp(context) }
                is VpnToggle.Result.NoProfile -> withContext(Dispatchers.Main) { VpnToggle.openApp(context) }
                is VpnToggle.Result.Failed -> withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Connection failed", Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
            val state = try {
                (context.applicationContext as VpnApplication).container.vpn.status.value.state
            } catch (_: Exception) { VpnState.DISCONNECTED }
            val profileName = try {
                (context.applicationContext as VpnApplication).container.vpn.status.value.profileId?.let { id ->
                    (context.applicationContext as VpnApplication).container.store.profiles.first().firstOrNull { it.id == id }?.name
                } ?: "VPN"
            } catch (_: Exception) { "VPN" }
            VpnNotification.show(context, profileName, 0, 0, state == VpnState.CONNECTED, state == VpnState.BLOCKED)
            VpnWidgetProvider.updateAll(context, state)
            pending.finish()
        }
    }

    private fun handleDisconnect(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as VpnApplication).container.vpn.disconnect()
            } catch (_: Exception) {}
            VpnNotification.cancel(context)
            VpnWidgetProvider.updateAll(context, VpnState.DISCONNECTED)
            pending.finish()
        }
    }
}
