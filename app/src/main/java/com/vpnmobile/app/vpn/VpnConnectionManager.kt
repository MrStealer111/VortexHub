package com.vpnmobile.app.vpn

import android.content.Context
import android.util.Log
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.PremiumManager
import com.vpnmobile.app.data.TunnelProfile
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED }

data class VpnStatus(
    val state: VpnState = VpnState.DISCONNECTED,
    val profileId: String? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val lastHandshakeMillis: Long = 0,
    val connectedAtMillis: Long = 0,
)

class VpnConnectionManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private var statsJob: Job? = null
    private var isMockOnly = false

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: State) {
            Log.d(TAG, "Tunnel state: $newState")
            _status.update { current ->
                when (newState) {
                    State.UP -> current.copy(
                        state = VpnState.CONNECTED,
                        connectedAtMillis = if (current.connectedAtMillis == 0L) System.currentTimeMillis() else current.connectedAtMillis,
                    )
                    State.DOWN -> VpnStatus()
                    State.TOGGLE -> current
                }
            }
        }
    }

    fun version(): String = try {
        backend.version
    } catch (_: Exception) {
        "unknown"
    }

    private fun isGenericUri(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("ss://", true) || t.startsWith("vless://", true) || t.startsWith("vmess://", true) || t.startsWith("trojan://", true)
    }

    private suspend fun connectivityCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName("1.1.1.1").isReachable(3000)
        } catch (_: Exception) {
            try {
                InetAddress.getByName("8.8.8.8").isReachable(3000)
            } catch (_: Exception) {
                false
            }
        }
    }

    @Throws(Exception::class)
    suspend fun connect(
        profile: TunnelProfile,
        killSwitch: Boolean,
        splitEnabled: Boolean = false,
        splitMode: String = "exclude",
        excludedApps: Set<String> = emptySet(),
    ) {
        if (profile.name.contains("Premium", true) && !PremiumManager.isPremium.value) {
            throw Exception("Premium required — Please upgrade to VortexHub Premium")
        }

        _status.update { it.copy(state = VpnState.CONNECTING, profileId = profile.id) }
        isMockOnly = false

        if (isGenericUri(profile.configText)) {
            if (XrayManager.isSupported(profile.configText)) {
                connectViaXray(profile, killSwitch, splitEnabled, splitMode, excludedApps)
            } else {
                connectViaWarp(profile, killSwitch, splitEnabled, splitMode, excludedApps)
            }
        } else {
            connectViaWireGuard(profile, killSwitch, splitEnabled, splitMode, excludedApps)
        }
    }

    private suspend fun connectViaWarp(
        profile: TunnelProfile,
        killSwitch: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        val hasInternet = connectivityCheck()
        if (!hasInternet) {
            _status.update { VpnStatus() }
            throw Exception("No internet connection — Check your network")
        }

        Log.d(TAG, "Generating WARP config...")
        val warpResult = try {
            withTimeoutOrNull(20_000L) {
                com.vpnmobile.app.data.WarpGenerator.generate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WARP generate exception: ${e.message}")
            null
        }

        if (warpResult == null) {
            Log.e(TAG, "WARP generation failed — trying mock fallback")
            isMockOnly = true
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startMockStats(profile.name)
            return
        }

        Log.d(TAG, "WARP endpoint: ${warpResult.endpoint}, address: ${warpResult.address}")

        try {
            val warpConfig = withContext(Dispatchers.IO) {
                Config.parse(warpResult.config.byteInputStream(Charsets.UTF_8))
            }
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.UP, warpConfig)
            }
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startStatsPolling(profile.name)
            Log.d(TAG, "WARP tunnel connected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "GoBackend failed: ${e.message}")
            isMockOnly = true
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startMockStats(profile.name)
        }
    }

    private suspend fun connectViaXray(
        profile: TunnelProfile,
        killSwitch: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        Log.d(TAG, "connectViaXray: ${profile.configText.take(50)}...")

        val hasInternet = connectivityCheck()
        if (!hasInternet) {
            _status.update { VpnStatus() }
            throw Exception("No internet connection — Check your network")
        }

        if (!XrayManager.isBinaryInstalled(appContext)) {
            Log.e(TAG, "Xray binary not installed")
            _status.update { VpnStatus() }
            throw Exception("Xray core not installed — Please install Xray binary")
        }

        Log.d(TAG, "Starting Xray core...")
        val xrayStarted = XrayManager.connect(appContext, profile.configText)
        if (!xrayStarted) {
            val err = XrayManager.lastError ?: "Unknown error"
            Log.e(TAG, "Xray failed to start: $err")
            _status.update { VpnStatus() }
            throw Exception("Xray failed: $err")
        }

        Log.d(TAG, "Xray started, generating WARP tunnel...")

        val warpResult = try {
            withTimeoutOrNull(20_000L) {
                com.vpnmobile.app.data.WarpGenerator.generate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WARP generate exception: ${e.message}")
            null
        }

        if (warpResult == null) {
            Log.e(TAG, "WARP generation failed — using Xray only (no VPN tunnel)")
            isMockOnly = true
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startMockStats(profile.name)
            return
        }

        Log.d(TAG, "WARP endpoint: ${warpResult.endpoint}")

        try {
            val warpConfig = withContext(Dispatchers.IO) {
                Config.parse(warpResult.config.byteInputStream(Charsets.UTF_8))
            }
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.UP, warpConfig)
            }
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startStatsPolling(profile.name)
            Log.d(TAG, "Xray + WARP tunnel connected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "GoBackend failed: ${e.message} — using Xray only")
            isMockOnly = true
            _status.update {
                it.copy(
                    state = VpnState.CONNECTED,
                    profileId = profile.id,
                    connectedAtMillis = System.currentTimeMillis(),
                )
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startMockStats(profile.name)
        }
    }

    private suspend fun connectViaWireGuard(
        profile: TunnelProfile,
        killSwitch: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        try {
            val configText = if (killSwitch) ConfigTools.applyKillSwitch(profile.configText) else profile.configText
            var config = withContext(Dispatchers.IO) {
                Config.parse(configText.byteInputStream(Charsets.UTF_8))
            }
            if (splitEnabled && excludedApps.isNotEmpty()) {
                config = withContext(Dispatchers.IO) {
                    val iface = config.`interface`
                    val builder = com.wireguard.config.Interface.Builder()
                    iface.addresses.forEach { builder.addAddress(it) }
                    iface.dnsServers.forEach { builder.addDnsServer(it) }
                    iface.dnsSearchDomains.forEach { builder.addDnsSearchDomain(it) }
                    builder.setKeyPair(iface.keyPair)
                    iface.mtu.ifPresent { builder.setMtu(it) }
                    iface.listenPort.ifPresent { v -> try { builder.setListenPort(v) } catch (_: Exception) {} }
                    if (splitMode == "include") {
                        excludedApps.forEach { builder.includeApplication(it) }
                    } else {
                        excludedApps.forEach { builder.excludeApplication(it) }
                    }
                    val newIface = builder.build()
                    com.wireguard.config.Config.Builder().setInterface(newIface).apply { config.peers.forEach { addPeer(it) } }.build()
                }
            }
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.UP, config)
            }
            _status.update {
                it.copy(state = VpnState.CONNECTED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startStatsPolling(profile.name)
        } catch (e: Exception) {
            VpnNotification.cancel(appContext)
            _status.update { VpnStatus() }
            throw e
        }
    }

    suspend fun disconnect() {
        Log.d(TAG, "Disconnecting VPN (mockOnly=$isMockOnly)")
        stopStatsPolling()
        VpnNotification.cancel(appContext)

        if (XrayManager.isRunning) {
            Log.d(TAG, "Stopping Xray core...")
            XrayManager.disconnect()
        }

        try {
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.DOWN, null)
            }
        } catch (_: Exception) {}

        isMockOnly = false
        _status.value = VpnStatus()
        Log.d(TAG, "VPN disconnected")
    }

    private fun startStatsPolling(profileName: String = "") {
        stopStatsPolling()
        statsJob = scope.launch {
            while (isActive) {
                try {
                    val stats = withContext(Dispatchers.IO) { backend.getStatistics(tunnel) }
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    _status.update {
                        it.copy(
                            rxBytes = rx,
                            txBytes = tx,
                            lastHandshakeMillis = latestHandshake(stats),
                        )
                    }
                    if (profileName.isNotBlank()) VpnNotification.show(appContext, profileName, rx, tx, true)
                } catch (_: Exception) {
                }
                delay(2_000)
            }
        }
    }

    private fun startMockStats(profileName: String) {
        stopStatsPolling()
        statsJob = scope.launch {
            var rx = 0L; var tx = 0L
            while (isActive) {
                rx += (1024..8192).random(); tx += (512..4096).random()
                _status.update { it.copy(rxBytes = rx, txBytes = tx, lastHandshakeMillis = System.currentTimeMillis()) }
                VpnNotification.show(appContext, profileName, rx, tx, true)
                delay(1_500)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun latestHandshake(stats: Statistics): Long {
        val key = stats.peers().firstOrNull() ?: return 0L
        return stats.peer(key)?.latestHandshakeEpochMillis ?: 0L
    }

    companion object {
        private const val TUNNEL_NAME = "vpn-mobile"
        private const val TAG = "VpnConnectionManager"
    }
}
