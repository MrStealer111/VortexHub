package com.vpnmobile.app.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process connection state shared by the service and [VpnConnectionManager]. */
object XrayRuntime {
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

    fun setBlocked() {
        lastError = null
        _state.value = VpnState.BLOCKED
    }

    fun reset() {
        lastError = null
        _state.value = VpnState.DISCONNECTED
    }
}

class XrayVpnService : VpnService() {

    companion object {
        private const val TAG = "XrayVpnService"
        private const val ACTION_CONNECT = "com.vpnmobile.app.action.XRAY_CONNECT"
        private const val ACTION_DISCONNECT = "com.vpnmobile.app.action.XRAY_DISCONNECT"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_SPLIT_ENABLED = "split_enabled"
        private const val EXTRA_SPLIT_MODE = "split_mode"
        private const val EXTRA_SPLIT_PACKAGES = "split_packages"
        private const val EXTRA_KILL_SWITCH = "kill_switch"
        private const val EXTRA_BLOCK_IPV6 = "block_ipv6"

        fun start(
            context: Context,
            profileName: String,
            configJson: String,
            host: String,
            port: Int,
            splitEnabled: Boolean,
            splitMode: String,
            splitPackages: Set<String>,
            killSwitch: Boolean = false,
            blockIpv6: Boolean = true,
        ) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_NAME, profileName)
                putExtra(EXTRA_CONFIG, configJson)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_SPLIT_ENABLED, splitEnabled)
                putExtra(EXTRA_SPLIT_MODE, splitMode)
                putStringArrayListExtra(EXTRA_SPLIT_PACKAGES, ArrayList(splitPackages))
                putExtra(EXTRA_KILL_SWITCH, killSwitch)
                putExtra(EXTRA_BLOCK_IPV6, blockIpv6)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, XrayVpnService::class.java).apply { action = ACTION_DISCONNECT }
            context.startService(intent)
        }
    }

    @Volatile
    private var starting = false
    @Volatile
    private var keepFd: FileDescriptor? = null
    @Volatile
    private var tunFdRaw: Int = -1
    @Volatile
    private var killSwitch = false
    private var blockIpv6 = true
    @Volatile
    private var monitor: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (starting) return START_NOT_STICKY
                if (keepFd != null) teardown()
                val name = intent.getStringExtra(EXTRA_NAME) ?: "VLESS/VMess/Trojan"
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                if (config.isNullOrBlank() || host.isNullOrBlank() || port !in 1..65535) {
                    XrayRuntime.setError("Invalid VLESS/VMess/Trojan configuration")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val splitEnabled = intent.getBooleanExtra(EXTRA_SPLIT_ENABLED, false)
                val splitMode = intent.getStringExtra(EXTRA_SPLIT_MODE) ?: "exclude"
                val splitPackages = intent.getStringArrayListExtra(EXTRA_SPLIT_PACKAGES)?.toSet() ?: emptySet()
                killSwitch = intent.getBooleanExtra(EXTRA_KILL_SWITCH, false)
                blockIpv6 = intent.getBooleanExtra(EXTRA_BLOCK_IPV6, true)
                startTunnel(name, config, host, port, splitEnabled, splitMode, splitPackages)
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
        configJson: String,
        host: String,
        port: Int,
        splitEnabled: Boolean,
        splitMode: String,
        splitPackages: Set<String>,
    ) {
        starting = true
        XrayRuntime.setConnecting()
        startForegroundNotification(name)
        Thread({
            var established = false
            try {
                val fd = establish(splitEnabled, splitMode, splitPackages)
                established = true
                // Clear close-on-exec and detach so the child process inherits the
                // TUN fd. A second dup stays in this process and keeps the TUN up
                // for the kill switch if Xray exits.
                keepFd = Os.dup(fd.fileDescriptor)
                val raw = fd.detachFd()
                tunFdRaw = raw
                // ProcessBuilder cannot inherit arbitrary descriptors on Android.
                // Put the TUN on stdin (fd 0) and let Xray use XRAY_TUN_FD=0.
                val rawPfd = ParcelFileDescriptor.adoptFd(raw)
                Os.dup2(rawPfd.fileDescriptor, 0)
                rawPfd.close()
                tunFdRaw = 0

                XrayProcess.start(this, configJson, 0).getOrThrow()
                XrayRuntime.setConnected()
                Log.d(TAG, "Xray tunnel connected to $host:$port (core ${XrayProcess.version()})")
                startMonitor(name, host, port)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start Xray tunnel", e)
                if (killSwitch && established) {
                    XrayRuntime.setBlocked()
                    VpnNotification.show(this, name, 0, 0, false, blocked = true)
                    Log.w(TAG, "Kill switch active — TUN kept open, traffic blocked")
                } else {
                    val message = friendlyError(e)
                    teardown()
                    XrayRuntime.setError(message)
                    stopSelf()
                }
            } finally {
                starting = false
            }
        }, "xray-connect").start()
    }

    private fun startMonitor(name: String, host: String, port: Int) {
        monitor?.interrupt()
        monitor = Thread({
            var upstreamFailures = 0
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(3_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (!XrayProcess.isRunning()) {
                    if (killSwitch) {
                        blockTraffic(name, "Tunnel lost — kill switch blocking traffic")
                    } else {
                        XrayRuntime.setError("Tunnel lost")
                        teardown()
                        stopSelf()
                    }
                    return@Thread
                }
                if (killSwitch) {
                    upstreamFailures = if (probeUpstream(host, port)) 0 else upstreamFailures + 1
                    if (upstreamFailures >= 3) {
                        blockTraffic(name, "Upstream unreachable — kill switch blocking traffic")
                        return@Thread
                    }
                }
            }
        }, "xray-monitor").apply { start() }
    }

    private fun probeUpstream(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            protect(socket)
            socket.connect(InetSocketAddress(host, port), 2_000)
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun blockTraffic(name: String, message: String) {
        XrayRuntime.setBlocked()
        VpnNotification.show(this, name, 0, 0, false, blocked = true)
        Log.w(TAG, message)
    }

    private fun establish(
        splitEnabled: Boolean,
        splitMode: String,
        splitPackages: Set<String>,
    ): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("VortexHub Xray")
            .setMtu(1500)
            .addAddress("10.111.222.1", 24)
            .addAddress("fd00:111:222::1", 64)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .addRoute("0.0.0.0", 0)
            .addRoute("1.1.1.1", 32)
            .addDisallowedApplication(packageName)
        if (blockIpv6) builder.addRoute("::", 0)
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
            message.contains("config parse", ignoreCase = true) -> "Invalid VLESS/VMess/Trojan configuration"
            message.contains("core init", ignoreCase = true) ||
                message.contains("core start", ignoreCase = true) -> "Xray could not start: $message"
            message.contains("tun fd", ignoreCase = true) -> "VPN tunnel could not be created"
            message.contains("protect", ignoreCase = true) -> "Could not protect Xray sockets"
            message.contains("Android 8.0", ignoreCase = true) -> "VLESS/VMess/Trojan need Android 8.0 or newer"
            message.contains("not bundled", ignoreCase = true) ||
                message.contains("device ABI", ignoreCase = true) -> "Xray engine unavailable on this device"
            message.contains("executable", ignoreCase = true) -> "Could not start the Xray engine"
            message.contains("Xray exited", ignoreCase = true) -> "Xray could not start — check the link and server"
            message.isBlank() -> "Unable to connect to the VLESS/VMess/Trojan server"
            else -> message
        }
    }

    private fun teardown(resetState: Boolean = true) {
        monitor?.interrupt()
        monitor = null
        runCatching { XrayProcess.stop() }
        // The child owns tunFdRaw; close only our kill-switch duplicate.
        tunFdRaw = -1
        keepFd?.let { descriptor ->
            try {
                Os.close(descriptor)
            } catch (_: Exception) {
            }
        }
        keepFd = null
        starting = false
        VpnNotification.cancel(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (resetState) XrayRuntime.reset()
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
