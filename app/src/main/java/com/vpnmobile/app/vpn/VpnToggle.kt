package com.vpnmobile.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.vpnmobile.app.MainActivity
import com.vpnmobile.app.VpnApplication
import com.vpnmobile.app.data.TunnelProfile
import kotlinx.coroutines.flow.first

/** Shared connect/disconnect action for the Quick Settings tile and home-screen widget. */
object VpnToggle {

    sealed class Result {
        object Connected : Result()
        object Disconnected : Result()
        object NeedsPermission : Result()
        object NoProfile : Result()
        data class Failed(val message: String) : Result()
    }

    suspend fun toggle(context: Context): Result {
        val app = context.applicationContext as? VpnApplication ?: return Result.Failed("App unavailable")
        val container = app.container
        val state = container.vpn.status.value.state
        if (state != VpnState.DISCONNECTED) {
            container.vpn.disconnect()
            return Result.Disconnected
        }
        if (VpnService.prepare(context) != null) return Result.NeedsPermission

        val profiles: List<TunnelProfile> = container.store.profiles.first()
        if (profiles.isEmpty()) return Result.NoProfile
        val selectedId = container.store.selectedProfileId.first()
        val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.last()
        return try {
            container.vpn.connect(
                profile,
                container.store.killSwitch.first(),
                container.store.blockIpv6.first(),
                container.store.splitEnabled.first(),
                container.store.splitMode.first(),
                container.store.excludedApps.first(),
            )
            Result.Connected
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Connection failed")
        }
    }

    fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }
}
