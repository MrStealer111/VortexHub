package com.vpnmobile.app.vpn

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.ShadowsocksUri
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.data.XrayConfig
import com.vpnmobile.app.util.Diagnostics
import com.vpnmobile.app.widget.VpnWidgetProvider
import com.wireguard.android.backend.BackendException as WgBackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.BadConfigException as WgBadConfigException
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, BLOCKED }

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

    private enum class Engine { NONE, WIREGUARD, SHADOWSOCKS, XRAY }

    init {
        scope.launch {
            _status.map { it.state }.distinctUntilChanged().collect { state ->
                VpnWidgetProvider.updateAll(appContext, state)
            }
        }
    }

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
        blockIpv6: Boolean = true,
        splitEnabled: Boolean = false,
        splitMode: String = "exclude",
        excludedApps: Set<String> = emptySet(),
    ) {
        val configText = profile.configText.trim()
        if (configText.isEmpty()) {
            throw Exception("Empty configuration — Import a WireGuard config, a Shadowsocks/VLESS/VMess/Trojan link, or use WARP")
        }
        _status.update { it.copy(state = VpnState.CONNECTING, profileId = profile.id) }

        try {
            when {
                isAutoWarp(configText) -> connectViaWarp(profile, blockIpv6, splitEnabled, splitMode, excludedApps)
                XrayConfig.isXrayUri(configText) ->
                    connectViaXray(profile, killSwitch, blockIpv6, splitEnabled, splitMode, excludedApps)
                ShadowsocksUri.isShadowsocks(configText) ->
                    connectViaShadowsocks(profile, killSwitch, blockIpv6, splitEnabled, splitMode, excludedApps)
                else -> connectViaWireGuard(profile, killSwitch, blockIpv6, splitEnabled, splitMode, excludedApps)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.javaClass.simpleName}", e)
            stopStatsPolling()
            if (ShadowsocksRuntime.state.value != VpnState.DISCONNECTED) {
                ShadowsocksVpnService.stop(appContext)
                ShadowsocksRuntime.reset()
            }
            if (XrayRuntime.state.value != VpnState.DISCONNECTED) {
                XrayVpnService.stop(appContext)
                XrayRuntime.reset()
            }
            activeEngine = Engine.NONE
            VpnNotification.cancel(appContext)
            _status.value = VpnStatus()
            if (e is WgBadConfigException) {
                throw Exception(
                    "Invalid WireGuard config: ${e.reason.name.lowercase().replace('_', ' ')} (${e.text})",
                    e,
                )
            }
            if (e is WgBackendException) {
                val detail = when (e.reason) {
                    WgBackendException.Reason.VPN_NOT_AUTHORIZED ->
                        "VPN permission was not granted — tap Connect and allow the VPN request"
                    WgBackendException.Reason.TUN_CREATION_ERROR ->
                        "Could not create the VPN tunnel — another VPN may be active"
                    WgBackendException.Reason.UNABLE_TO_START_VPN ->
                        "Could not start the VPN service"
                    WgBackendException.Reason.DNS_RESOLUTION_FAILURE ->
                        "Could not resolve the server address"
                    WgBackendException.Reason.TUNNEL_MISSING_CONFIG ->
                        "WireGuard config is missing"
                    WgBackendException.Reason.GO_ACTIVATION_ERROR_CODE ->
                        "WireGuard failed to start (${e.format.joinToString()})"
                    else -> "VPN connection failed (${e.reason.name})"
                }
                throw Exception(detail, e)
            }
            throw e
        }
    }

    private suspend fun connectViaWarp(
        profile: TunnelProfile,
        blockIpv6: Boolean,
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

        val warpText = if (blockIpv6) ConfigTools.applyIpv6Block(warpResult.config) else warpResult.config
        val warpConfig = withContext(Dispatchers.IO) {
            Config.parse(warpText.byteInputStream(Charsets.UTF_8))
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
        blockIpv6: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        var configText = ConfigTools.normalizeWireGuard(profile.configText)
            ?: throw Exception("Invalid WireGuard config — paste the [Interface] text or a base64 wireguard:// link")
        if (blockIpv6) configText = ConfigTools.applyIpv6Block(configText)
        if (killSwitch) configText = ConfigTools.applyKillSwitch(configText)
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
        killSwitch: Boolean,
        blockIpv6: Boolean,
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
        Diagnostics.log(TAG, "Connecting Shadowsocks to ${config.host}:${config.port} (killSwitch=$killSwitch)")
        ShadowsocksRuntime.reset()
        ShadowsocksVpnService.start(
            appContext, profile.name, config, splitEnabled, splitMode, excludedApps, killSwitch, blockIpv6,
        )

        val result = withTimeoutOrNull(25_000) {
            ShadowsocksRuntime.state.first {
                it == VpnState.CONNECTED || it == VpnState.BLOCKED ||
                    (it == VpnState.DISCONNECTED && ShadowsocksRuntime.lastError != null)
            }
        }
        if (result == VpnState.CONNECTED) {
            activeEngine = Engine.SHADOWSOCKS
            _status.update {
                it.copy(state = VpnState.CONNECTED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startShadowsocksStatsPolling(profile.name)
            Log.d(TAG, "Shadowsocks tunnel connected")
            return
        }
        if (result == VpnState.BLOCKED || (killSwitch && result == null)) {
            activeEngine = Engine.SHADOWSOCKS
            ShadowsocksRuntime.setBlocked()
            _status.update {
                it.copy(state = VpnState.BLOCKED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
            }
            VpnNotification.show(appContext, profile.name, 0, 0, false, blocked = true)
            VpnNotification.showKillSwitchAlert(appContext, profile.name)
            startShadowsocksStatsPolling(profile.name)
            Diagnostics.log(TAG, "Shadowsocks blocked by kill switch")
            return
        }
        ShadowsocksVpnService.stop(appContext)
        throw Exception(ShadowsocksRuntime.lastError ?: "Shadowsocks connection timed out")
    }

    private suspend fun connectViaXray(
        profile: TunnelProfile,
        killSwitch: Boolean,
        blockIpv6: Boolean,
        splitEnabled: Boolean,
        splitMode: String,
        excludedApps: Set<String>,
    ) {
        if (!connectivityCheck()) {
            throw Exception("No internet connection — Check your network")
        }

        val endpoint = XrayConfig.parseEndpoint(profile.configText)
            ?: throw Exception("Invalid VLESS/VMess/Trojan link")
        val configJson = XrayConfig.build(profile.configText)
        Log.d(TAG, "Connecting ${XrayConfig.protocolName(profile.configText)} to ${endpoint.first}:${endpoint.second}")
        Diagnostics.log(TAG, "Connecting Xray to ${endpoint.first}:${endpoint.second} (killSwitch=$killSwitch)")

        XrayRuntime.reset()
        XrayVpnService.start(
            appContext,
            profile.name,
            configJson,
            endpoint.first,
            endpoint.second,
            splitEnabled,
            splitMode,
            excludedApps,
            killSwitch,
            blockIpv6,
        )

        val result = withTimeoutOrNull(25_000) {
            XrayRuntime.state.first {
                it == VpnState.CONNECTED || it == VpnState.BLOCKED ||
                    (it == VpnState.DISCONNECTED && XrayRuntime.lastError != null)
            }
        }
        if (result == VpnState.CONNECTED) {
            val verified = withContext(Dispatchers.IO) {
                listOf(
                    "https://ipwho.is/",
                    "https://www.google.com/generate_204",
                    "https://cp.cloudflare.com/generate_204",
                ).any { endpoint ->
                    runCatching {
                        val connection = URL(endpoint).openConnection(
                            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
                        ) as HttpURLConnection
                        try {
                            connection.connectTimeout = 4_000
                            connection.readTimeout = 4_000
                            connection.requestMethod = "GET"
                            connection.responseCode in 200..399
                        } finally {
                            connection.disconnect()
                        }
                    }.getOrDefault(false)
                }
            }
            if (!verified) {
                XrayVpnService.stop(appContext)
                XrayRuntime.reset()
                throw Exception("Server connected but internet is unreachable — check the key or server")
            }
            activeEngine = Engine.XRAY
            _status.update {
                it.copy(state = VpnState.CONNECTED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
            }
            VpnNotification.show(appContext, profile.name, 0, 0, true)
            startXrayStatsPolling(profile.name)
            Log.d(TAG, "Xray tunnel connected")
            return
        }
        if (result == VpnState.BLOCKED || (killSwitch && result == null)) {
            activeEngine = Engine.XRAY
            XrayRuntime.setBlocked()
            _status.update {
                it.copy(state = VpnState.BLOCKED, profileId = profile.id, connectedAtMillis = System.currentTimeMillis())
            }
            VpnNotification.show(appContext, profile.name, 0, 0, false, blocked = true)
            VpnNotification.showKillSwitchAlert(appContext, profile.name)
            startXrayStatsPolling(profile.name)
            Diagnostics.log(TAG, "Xray blocked by kill switch")
            return
        }
        XrayVpnService.stop(appContext)
        throw Exception(XrayRuntime.lastError ?: "VLESS/VMess/Trojan connection timed out")
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
        val xrayActive = activeEngine == Engine.XRAY ||
            XrayRuntime.state.value != VpnState.DISCONNECTED
        if (xrayActive) {
            XrayVpnService.stop(appContext)
            XrayRuntime.reset()
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
                when (ShadowsocksRuntime.state.value) {
                    VpnState.CONNECTED -> {
                        val rx = (TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 }?.minus(baseRx) ?: 0L).coerceAtLeast(0L)
                        val tx = (TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 }?.minus(baseTx) ?: 0L).coerceAtLeast(0L)
                        _status.update { it.copy(rxBytes = rx, txBytes = tx) }
                        VpnNotification.show(appContext, profileName, rx, tx, true)
                    }
                    VpnState.BLOCKED -> {
                        _status.update { it.copy(state = VpnState.BLOCKED) }
                        VpnNotification.show(appContext, profileName, 0, 0, false, blocked = true)
                    }
                    else -> {
                        VpnNotification.cancel(appContext)
                        _status.value = VpnStatus()
                        break
                    }
                }
                delay(2_000)
            }
        }
    }

    private fun startXrayStatsPolling(profileName: String) {
        stopStatsPolling()
        val uid = Process.myUid()
        val baseRx = TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 } ?: 0L
        val baseTx = TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 } ?: 0L
        statsJob = scope.launch {
            while (isActive) {
                when (XrayRuntime.state.value) {
                    VpnState.CONNECTED -> {
                        val rx = (TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 }?.minus(baseRx) ?: 0L).coerceAtLeast(0L)
                        val tx = (TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 }?.minus(baseTx) ?: 0L).coerceAtLeast(0L)
                        _status.update { it.copy(rxBytes = rx, txBytes = tx) }
                        VpnNotification.show(appContext, profileName, rx, tx, true)
                    }
                    VpnState.BLOCKED -> {
                        _status.update { it.copy(state = VpnState.BLOCKED) }
                        VpnNotification.show(appContext, profileName, 0, 0, false, blocked = true)
                    }
                    else -> {
                        VpnNotification.cancel(appContext)
                        _status.value = VpnStatus()
                        break
                    }
                }
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
