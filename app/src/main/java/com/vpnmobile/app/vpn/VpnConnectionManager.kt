package com.vpnmobile.app.vpn

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.PremiumManager
import com.vpnmobile.app.data.ShadowsocksUri
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
import kotlinx.coroutines.flow.first
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
    private var activeEngine = Engine.NONE

    private enum class Engine { NONE, WIREGUARD, SHADOWSOCKS }

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

    private fun isUnsupportedUri(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("vless://", true) || t.startsWith("vmess://", true) || t.startsWith("trojan://", true)
    }

    private fun isAutoWarp(text: String): Boolean {
        val t = text.trim().lowercase()
        return t == "warp://auto" || t == "warp://" || t.startsWith("vless://warp@")
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

        val configText = profile.configText.trim()
        if (configText.isEmpty()) {
            throw Exception("Empty configuration — Import a WireGuard config, a Shadowsocks link, or use WARP")
        }
        if (isUnsupportedUri(configText) && !isAutoWarp(configText)) {
            throw Exception("This protocol is not supported in this build yet. Please use WireGuard, Shadowsocks, or WARP.")
        }

        _status.update { it.copy(state = VpnState.CONNECTING, profileId = profile.id) }

        try {
            when {
                isAutoWarp(configText) -> connectViaWarp(profile, splitEnabled, splitMode, excludedApps)
                ShadowsocksUri.isShadowsocks(configText) -> connectViaShadowsocks(profile, splitEnabled, splitMode, excludedApps)
                else -> connectViaWireGuard(profile, killSwitch, splitEnabled, splitMode, excludedApps)
            }
        } catch (e: Exception) {
            stopStatsPolling()
            if (ShadowsocksRuntime.state.value != VpnState.DISCONNECTED) {
                ShadowsocksVpnService.stop(appContext)
                ShadowsocksRuntime.reset()
            }
            activeEngine = Engine.NONE
            VpnNotification.cancel(appContext)
            _status.value = VpnStatus()
            throw e
        }
    }

    private suspend fun connectViaWarp(
        profile: TunnelProfile,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        if (!connectivityCheck()) {
            throw Exception("No internet connection — Check your network")
        }

        Log.d(TAG, "Generating WARP config...")
        val warpResult = com.vpnmobile.app.data.WarpGenerator.generate()

        Log.d(TAG, "WARP endpoint: ${warpResult.endpoint}, address: ${warpResult.address}")

        val warpConfig = withContext(Dispatchers.IO) {
            Config.parse(warpResult.config.byteInputStream(Charsets.UTF_8))
        }
        val finalConfig = applySplitTunnel(warpConfig, splitEnabled, splitMode, excludedApps)

        withContext(Dispatchers.IO) {
            backend.setState(tunnel, State.UP, finalConfig)
        }
        activeEngine = Engine.WIREGUARD
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
    }

    private suspend fun connectViaWireGuard(
        profile: TunnelProfile,
        killSwitch: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        val configText = if (killSwitch) ConfigTools.applyKillSwitch(profile.configText) else profile.configText
        val baseConfig = withContext(Dispatchers.IO) {
            Config.parse(configText.byteInputStream(Charsets.UTF_8))
        }
        val finalConfig = applySplitTunnel(baseConfig, splitEnabled, splitMode, excludedApps)

        withContext(Dispatchers.IO) {
            backend.setState(tunnel, State.UP, finalConfig)
        }
        activeEngine = Engine.WIREGUARD
        _status.update {
            it.copy(state = VpnState.CONNECTED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
        }
        VpnNotification.show(appContext, profile.name, 0, 0, true)
        startStatsPolling(profile.name)
    }

    private suspend fun connectViaShadowsocks(
        profile: TunnelProfile,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        if (!connectivityCheck()) {
            throw Exception("No internet connection — Check your network")
        }

        val config = ShadowsocksUri.parse(profile.configText)
            ?: throw Exception("Invalid Shadowsocks link — expected ss://method:password@host:port")

        Log.d(TAG, "Connecting Shadowsocks to ${config.host}:${config.port}")
        ShadowsocksRuntime.reset()
        ShadowsocksVpnService.start(appContext, profile.name, config, splitEnabled, splitMode, excludedApps)

        val result = withTimeoutOrNull(25_000) {
            ShadowsocksRuntime.state.first {
                it == VpnState.CONNECTED || (it == VpnState.DISCONNECTED && ShadowsocksRuntime.lastError != null)
            }
        }
        if (result != VpnState.CONNECTED) {
            ShadowsocksVpnService.stop(appContext)
            throw Exception(ShadowsocksRuntime.lastError ?: "Shadowsocks connection timed out")
        }

        activeEngine = Engine.SHADOWSOCKS
        _status.update {
            it.copy(state = VpnState.CONNECTED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
        }
        VpnNotification.show(appContext, profile.name, 0, 0, true)
        startShadowsocksStatsPolling(profile.name)
        Log.d(TAG, "Shadowsocks tunnel connected")
    }

    private fun applySplitTunnel(
        config: Config,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ): Config {
        if (!splitEnabled || excludedApps.isEmpty()) return config
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
        return com.wireguard.config.Config.Builder().setInterface(newIface).apply { config.peers.forEach { addPeer(it) } }.build()
    }

    suspend fun disconnect() {
        Log.d(TAG, "Disconnecting VPN")
        stopStatsPolling()
        VpnNotification.cancel(appContext)
        val shadowsocksActive = activeEngine == Engine.SHADOWSOCKS ||
            ShadowsocksRuntime.state.value != VpnState.DISCONNECTED
        if (shadowsocksActive) {
            ShadowsocksVpnService.stop(appContext)
            ShadowsocksRuntime.reset()
        }
        try {
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.DOWN, null)
            }
        } catch (_: Exception) {}
        activeEngine = Engine.NONE
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

    private fun startShadowsocksStatsPolling(profileName: String) {
        stopStatsPolling()
        val uid = Process.myUid()
        val baseRx = TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 } ?: 0L
        val baseTx = TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 } ?: 0L
        statsJob = scope.launch {
            while (isActive) {
                if (ShadowsocksRuntime.state.value != VpnState.CONNECTED) {
                    VpnNotification.cancel(appContext)
                    _status.value = VpnStatus()
                    break
                }
                val rx = (TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 }?.minus(baseRx) ?: 0L).coerceAtLeast(0L)
                val tx = (TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 }?.minus(baseTx) ?: 0L).coerceAtLeast(0L)
                _status.update { it.copy(rxBytes = rx, txBytes = tx) }
                VpnNotification.show(appContext, profileName, rx, tx, true)
                delay(2_000)
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
