package com.vpnmobile.app.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vpnmobile.app.data.ShadowsocksConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import shadowsocks.Client
import shadowsocks.Shadowsocks
import tun2socks.Tun2socks
import tun2socks.Tunnel

/** In-process connection state shared by the service and [VpnConnectionManager]. */
object ShadowsocksRuntime {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    fun setConnecting() {
        lastError = null
        _state.value = VpnState.CONNECTING
    }

    fun setConnected() {
        lastError = null
        _state.value = VpnState.CONNECTED
    }

    fun setError(message: String) {
        lastError = message
        _state.value = VpnState.DISCONNECTED
    }

    fun reset() {
        lastError = null
        _state.value = VpnState.DISCONNECTED
    }
}

class ShadowsocksVpnService : VpnService() {

    companion object {
        private const val TAG = "ShadowsocksVpnService"
        private const val ACTION_CONNECT = "com.vpnmobile.app.action.SS_CONNECT"
        private const val ACTION_DISCONNECT = "com.vpnmobile.app.action.SS_DISCONNECT"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_METHOD = "method"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_SPLIT_ENABLED = "split_enabled"
        private const val EXTRA_SPLIT_MODE = "split_mode"
        private const val EXTRA_SPLIT_PACKAGES = "split_packages"

        fun start(
            context: Context,
            profileName: String,
            config: ShadowsocksConfig,
            splitEnabled: Boolean,
            splitMode: String,
            splitPackages: Set<String>,
        ) {
            val intent = Intent(context, ShadowsocksVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_NAME, profileName)
                putExtra(EXTRA_HOST, config.host)
                putExtra(EXTRA_PORT, config.port)
                putExtra(EXTRA_METHOD, config.method)
                putExtra(EXTRA_PASSWORD, config.password)
                putExtra(EXTRA_SPLIT_ENABLED, splitEnabled)
                putExtra(EXTRA_SPLIT_MODE, splitMode)
                putStringArrayListExtra(EXTRA_SPLIT_PACKAGES, ArrayList(splitPackages))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShadowsocksVpnService::class.java).apply { action = ACTION_DISCONNECT }
            context.startService(intent)
        }
    }

    @Volatile
    private var starting = false
    @Volatile
    private var tunnel: Tunnel? = null
    @Volatile
    private var client: Client? = null
    @Volatile
    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (starting) return START_NOT_STICKY
                if (tunnel != null) teardown()
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Shadowsocks"
                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                val method = intent.getStringExtra(EXTRA_METHOD)
                val password = intent.getStringExtra(EXTRA_PASSWORD)
                if (host.isNullOrBlank() || port !in 1..65535 || method.isNullOrBlank() || password.isNullOrBlank()) {
                    ShadowsocksRuntime.setError("Invalid Shadowsocks configuration")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val config = ShadowsocksConfig(host, port, method, password)
                val splitEnabled = intent.getBooleanExtra(EXTRA_SPLIT_ENABLED, false)
                val splitMode = intent.getStringExtra(EXTRA_SPLIT_MODE) ?: "exclude"
                val splitPackages = intent.getStringArrayListExtra(EXTRA_SPLIT_PACKAGES)?.toSet() ?: emptySet()
                startTunnel(name, config, splitEnabled, splitMode, splitPackages)
            }
            ACTION_DISCONNECT -> {
                teardown()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(
        name: String,
        config: ShadowsocksConfig,
        splitEnabled: Boolean,
        splitMode: String,
        splitPackages: Set<String>,
    ) {
        starting = true
        ShadowsocksRuntime.setConnecting()
        startForegroundNotification(name)
        Thread({
            try {
                val fd = establish(splitEnabled, splitMode, splitPackages)
                val client = Shadowsocks.newClientFromJSON(config.toClientJson())
                val tunnel = Tun2socks.connectShadowsocksTunnel(fd.fd.toLong(), client, true)
                this.tunFd = fd
                this.client = client
                this.tunnel = tunnel
                ShadowsocksRuntime.setConnected()
                Log.d(TAG, "Shadowsocks tunnel connected to ${config.host}:${config.port}")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start Shadowsocks tunnel", e)
                val message = friendlyError(e)
                teardown()
                ShadowsocksRuntime.setError(message)
                stopSelf()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Shadowsocks native library missing", e)
                teardown()
                ShadowsocksRuntime.setError("Shadowsocks engine unavailable on this device")
                stopSelf()
            } finally {
                starting = false
            }
        }, "shadowsocks-connect").start()
    }

    private fun establish(
        splitEnabled: Boolean,
        splitMode: String,
        splitPackages: Set<String>,
    ): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("VortexHub")
            .setMtu(1500)
            .addAddress("10.111.222.1", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .addRoute("1.1.1.1", 32)
            .addDisallowedApplication(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        if (splitEnabled && splitPackages.isNotEmpty()) {
            if (splitMode == "include") {
                splitPackages.forEach { builder.addAllowedApplication(it) }
            } else {
                splitPackages.forEach { builder.addDisallowedApplication(it) }
            }
        }
        return builder.establish() ?: throw IllegalStateException("VPN permission not granted")
    }

    private fun startForegroundNotification(name: String) {
        val notification = VpnNotification.build(this, name, 0, 0, false)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                VpnNotification.NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(VpnNotification.NOTIF_ID, notification)
        }
    }

    private fun friendlyError(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            message.contains("permission", ignoreCase = true) -> "VPN permission not granted"
            message.contains("resolve", ignoreCase = true) -> "Cannot resolve server address"
            message.contains("cipher", ignoreCase = true) || message.contains("method", ignoreCase = true) ->
                "Unsupported Shadowsocks cipher"
            message.isBlank() -> "Unable to connect to the Shadowsocks server"
            else -> message
        }
    }

    private fun teardown(resetState: Boolean = true) {
        try {
            tunnel?.disconnect()
        } catch (_: Exception) {
        }
        tunnel = null
        client = null
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
        starting = false
        VpnNotification.cancel(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (resetState) ShadowsocksRuntime.reset()
    }

    override fun onRevoke() {
        teardown()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown(resetState = false)
        super.onDestroy()
    }
}
