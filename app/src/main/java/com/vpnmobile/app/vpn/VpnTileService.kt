package com.vpnmobile.app.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vpnmobile.app.VpnApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VpnTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            when (VpnToggle.toggle(this@VpnTileService)) {
                is VpnToggle.Result.NeedsPermission -> requestPermission()
                is VpnToggle.Result.NoProfile -> VpnToggle.openApp(this@VpnTileService)
                else -> Unit
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun requestPermission() {
        val intent = VpnService.prepare(this) ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val app = application as? VpnApplication ?: return
        val state = app.container.vpn.status.value.state
        qsTile?.apply {
            this.state = if (state == VpnState.CONNECTED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "VortexHub"
            updateTile()
        }
    }
}
