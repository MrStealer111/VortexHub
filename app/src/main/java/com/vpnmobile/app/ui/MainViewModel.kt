package com.vpnmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vpnmobile.app.R
import com.vpnmobile.app.VpnApplication
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.Subscription
import com.vpnmobile.app.data.SubscriptionFetcher
import com.vpnmobile.app.data.SubscriptionParser
import com.vpnmobile.app.data.SubscriptionRefresher
import com.vpnmobile.app.work.SubscriptionRefreshScheduler
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.util.Diagnostics
import com.vpnmobile.app.vpn.VpnNotification
import com.vpnmobile.app.vpn.VpnState
import com.wireguard.android.backend.BackendException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VpnApplication).container
    private val store = container.store
    private val vpn = container.vpn

    val profiles: StateFlow<List<TunnelProfile>> =
        store.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val status = vpn.status

    val autoConnect: StateFlow<Boolean> =
        store.autoConnect.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val killSwitch: StateFlow<Boolean> =
        store.killSwitch.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val splitEnabled: StateFlow<Boolean> =
        store.splitEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val splitMode: StateFlow<String> =
        store.splitMode.stateIn(viewModelScope, SharingStarted.Eagerly, "exclude")

    val excludedApps: StateFlow<Set<String>> =
        store.excludedApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val serverUrl: StateFlow<String> =
        store.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val customDns: StateFlow<String> =
        store.customDns.stateIn(viewModelScope, SharingStarted.Eagerly, "1.1.1.1, 1.0.0.1")

    val autoReconnect: StateFlow<Boolean> =
        store.autoReconnect.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val blockIpv6: StateFlow<Boolean> =
        store.blockIpv6.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val biometricLock: StateFlow<Boolean> =
        store.biometricLock.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val secureScreen: StateFlow<Boolean> =
        store.secureScreen.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hideKeys: StateFlow<Boolean> =
        store.hideKeys.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val themeMode: StateFlow<String> =
        store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val accentColor: StateFlow<String> =
        store.accentColor.stateIn(viewModelScope, SharingStarted.Eagerly, "orange")

    val autoSortByLatency: StateFlow<Boolean> =
        store.autoSortByLatency.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val language: StateFlow<String> =
        store.language.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val autoRefreshInterval: StateFlow<String> =
        store.autoRefreshInterval.stateIn(viewModelScope, SharingStarted.Eagerly, "off")

    val favorites: StateFlow<Set<String>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val connectionHistory: StateFlow<List<String>> =
        store.connectionHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subscriptions: StateFlow<List<Subscription>> =
        store.subscriptions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _subscriptionRefreshing = MutableStateFlow<Set<String>>(emptySet())
    val subscriptionRefreshing: StateFlow<Set<String>> = _subscriptionRefreshing

    private val _findingFastest = MutableStateFlow(false)
    val findingFastest: StateFlow<Boolean> = _findingFastest

    val selectedProfile: StateFlow<TunnelProfile?> = combine(
        store.profiles,
        store.selectedProfileId,
    ) { list, selectedId ->
        list.firstOrNull { it.id == selectedId } ?: list.lastOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _freeSelected = MutableStateFlow<TunnelProfile?>(null)
    val freeSelected: StateFlow<TunnelProfile?> = _freeSelected

    fun selectFree(profile: TunnelProfile) { _freeSelected.value = profile }

    fun connectFree(profile: TunnelProfile) {
        _freeSelected.value = profile
        connect(profile)
    }

    val connectedProfile: StateFlow<TunnelProfile?> = combine(profiles, status) { list, current ->
        list.firstOrNull { it.id == current.profileId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val backendVersion: StateFlow<String?> = flow { emit(vpn.version()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _needPremium = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val needPremium: SharedFlow<Unit> = _needPremium

    private val _pingResults = MutableStateFlow<Map<String, PingTester.Result>>(emptyMap())
    val pingResults: StateFlow<Map<String, PingTester.Result>> = _pingResults

    private val _pingLoading = MutableStateFlow<Set<String>>(emptySet())
    val pingLoading: StateFlow<Set<String>> = _pingLoading

    private var pendingConnect: TunnelProfile? = null

    init {
        viewModelScope.launch {
            var savedProfiles = store.profiles.first()
            if (savedProfiles.isEmpty()) {
                store.addProfile("WarpGuard Free", "🌐", "warp://auto")
                savedProfiles = store.profiles.first()
            }
            val autoConnectEnabled = store.autoConnect.first()
            if (autoConnectEnabled && savedProfiles.isNotEmpty() && vpn.status.value.state == VpnState.DISCONNECTED) {
                connect(savedProfiles.first())
            }
        }
    }

    fun connect(profile: TunnelProfile) {
        viewModelScope.launch {
            try {
                vpn.connect(
                    profile,
                    killSwitch.value,
                    blockIpv6.value,
                    splitEnabled.value,
                    splitMode.value,
                    excludedApps.value,
                )
                store.addToHistory(profile.id)
                _messages.tryEmit(getApplication<Application>().getString(R.string.server_connected, profile.name))
            } catch (e: BackendException) {
                handleBackendError(profile, e)
            } catch (e: Exception) {
                if ((e.message ?: "").contains("Premium required", true)) {
                    _needPremium.tryEmit(Unit)
                    _messages.tryEmit("Premium required — Upgrade to VortexHub Premium")
                } else {
                    _messages.tryEmit(getApplication<Application>().getString(R.string.error_unknown, e.message ?: ""))
                    if (autoReconnect.value && status.value.state == VpnState.DISCONNECTED) {
                        kotlinx.coroutines.delay(3000)
                        connect(profile)
                    }
                }
            }
        }
    }

    private fun handleBackendError(profile: TunnelProfile, e: BackendException) {
        val app = getApplication<Application>()
        when (e.reason) {
            BackendException.Reason.VPN_NOT_AUTHORIZED -> {
                pendingConnect = profile
                _needsPermission.value = true
            }
            BackendException.Reason.DNS_RESOLUTION_FAILURE ->
                _messages.tryEmit(app.getString(R.string.error_dns))
            BackendException.Reason.TUN_CREATION_ERROR ->
                _messages.tryEmit(app.getString(R.string.error_tun))
            BackendException.Reason.UNABLE_TO_START_VPN ->
                _messages.tryEmit(app.getString(R.string.error_start))
            else ->
                _messages.tryEmit(app.getString(R.string.error_unknown, e.message ?: ""))
        }
    }

    /** Called right before the system VPN-permission dialog is shown. */
    fun prepareForPermission(profile: TunnelProfile) {
        pendingConnect = profile
        _needsPermission.value = true
    }

    fun onVpnPermissionGranted() {
        _needsPermission.value = false
        val pending = pendingConnect
        pendingConnect = null
        if (pending != null) connect(pending)
    }

    fun dismissPermissionPrompt() {
        pendingConnect = null
        _needsPermission.value = false
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                vpn.disconnect()
            } catch (e: Exception) {
                _messages.tryEmit(getApplication<Application>().getString(R.string.error_unknown, e.message ?: ""))
            }
        }
    }

    fun reconnect(profile: TunnelProfile) {
        viewModelScope.launch {
            try {
                vpn.disconnect()
            } catch (_: Exception) {
            }
            delay(600)
            connect(profile)
        }
    }

    fun toggleFavorite(profile: TunnelProfile) {
        viewModelScope.launch { store.toggleFavorite(profile.id) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { store.setAutoReconnect(enabled) }
    }

    fun addProfile(name: String, flag: String, configText: String) {
        viewModelScope.launch {
            store.addProfile(name.trim(), flag.trim().ifEmpty { "\uD83C\uDF10" }, configText.trim())
            _messages.tryEmit(getApplication<Application>().getString(R.string.config_imported))
        }
    }

    fun deleteProfile(profile: TunnelProfile) {
        viewModelScope.launch {
            if (status.value.profileId == profile.id && status.value.state != VpnState.DISCONNECTED) {
                vpn.disconnect()
            }
            store.deleteProfile(profile.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.server_removed))
        }
    }

    fun selectProfile(profile: TunnelProfile) {
        viewModelScope.launch { store.setSelectedProfile(profile.id) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { store.setAutoConnect(enabled) }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch { store.setKillSwitch(enabled) }
    }

    fun setBlockIpv6(enabled: Boolean) {
        viewModelScope.launch { store.setBlockIpv6(enabled) }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { store.setBiometricLock(enabled) }
    }

    fun setSecureScreen(enabled: Boolean) {
        viewModelScope.launch { store.setSecureScreen(enabled) }
    }

    fun setHideKeys(enabled: Boolean) {
        viewModelScope.launch { store.setHideKeys(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch { store.setAccentColor(color) }
    }

    fun setAutoRefreshInterval(interval: String) {
        viewModelScope.launch {
            store.setAutoRefreshInterval(interval)
            SubscriptionRefreshScheduler.sync(getApplication(), interval)
            _messages.tryEmit(if (interval == "off") "Auto-refresh off" else "Auto-refresh every ${interval}h")
        }
    }

    private val _subHealth = MutableStateFlow<Map<String, SubHealth>>(emptyMap())
    val subHealth: StateFlow<Map<String, SubHealth>> = _subHealth
    private val _healthChecking = MutableStateFlow(false)
    val healthChecking: StateFlow<Boolean> = _healthChecking

    fun checkSubscriptionHealth() {
        if (_healthChecking.value) return
        viewModelScope.launch {
            _healthChecking.value = true
            val result = mutableMapOf<String, SubHealth>()
            subscriptions.value.forEach { sub ->
                val configs = profiles.value.filter { it.subscriptionId == sub.id }
                var healthy = 0
                configs.forEach { p ->
                    val r = runCatching { PingTester.ping(p.configText) }.getOrNull()
                    if (r?.success == true) healthy++
                }
                result[sub.id] = SubHealth(healthy, configs.size)
            }
            _subHealth.value = result
            _healthChecking.value = false
        }
    }

    data class SubHealth(val healthy: Int, val total: Int)

    fun setAutoSortByLatency(enabled: Boolean) {
        viewModelScope.launch { store.setAutoSortByLatency(enabled) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { store.setLanguage(language) }
    }

    fun setSplitEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setSplitEnabled(enabled) }
    }

    fun setSplitMode(mode: String) {
        viewModelScope.launch { store.setSplitMode(mode) }
    }

    fun toggleSplitApp(packageName: String) {
        viewModelScope.launch { store.toggleExcludedApp(packageName) }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { store.setServerUrl(url) }
    }

    fun setCustomDns(dns: String) {
        viewModelScope.launch { store.setCustomDns(dns) }
    }

    fun testPing(profile: TunnelProfile) {
        viewModelScope.launch {
            _pingLoading.value = _pingLoading.value + profile.id
            val result = PingTester.ping(profile.configText)
            _pingResults.value = _pingResults.value + (profile.id to result)
            _pingLoading.value = _pingLoading.value - profile.id
        }
    }

    fun testAllPings() {
        viewModelScope.launch {
            profiles.value.forEach { testPing(it) }
        }
    }

    fun addSubscription(url: String, name: String = "") {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            _messages.tryEmit("Subscription URL must start with https://")
            return
        }
        viewModelScope.launch {
            val display = name.trim().ifBlank { subscriptionHost(trimmed) }
            if (subscriptions.value.any { it.url == trimmed }) {
                _messages.tryEmit("Subscription already added")
                return@launch
            }
            try {
                val sub = store.addSubscription(display, trimmed)
                Diagnostics.log("Subscription", "Added ${sub.name}")
                refreshSubscription(sub)
            } catch (e: Exception) {
                Diagnostics.log("Subscription", e)
                _messages.tryEmit("Could not add subscription: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun refreshSubscriptions() {
        viewModelScope.launch {
            val list = subscriptions.value
            if (list.isEmpty()) {
                _messages.tryEmit("No subscriptions to update")
                return@launch
            }
            list.forEach { refreshSubscription(it) }
        }
    }

    fun refreshOne(sub: Subscription) {
        viewModelScope.launch { refreshSubscription(sub) }
    }

    private suspend fun refreshSubscription(sub: Subscription) {
        _subscriptionRefreshing.update { it + sub.id }
        try {
            val outcome = SubscriptionRefresher.refresh(store, sub)
            if (outcome.total == 0) {
                _messages.tryEmit("${sub.name}: no supported configs found")
                return
            }
            Diagnostics.log("Subscription", "${sub.name}: ${outcome.total} configs")
            val suffix = if (!outcome.isFirstLoad && outcome.newCount > 0) ", ${outcome.newCount} new" else ""
            val usageNote = outcome.usage?.let { usage ->
                when {
                    usage.isExpired() -> ", expired"
                    usage.isExpiringSoon() -> {
                        val days = usage.daysLeft()
                        if (days <= 0L) ", expires today" else ", expires in ${days}d"
                    }
                    usage.isLowData() -> ", ${(usage.fractionUsed() * 100).toInt().coerceIn(0, 100)}% used"
                    else -> ""
                }
            }.orEmpty()
            _messages.tryEmit("${sub.name}: ${outcome.total} configs updated$suffix$usageNote")
            if (!outcome.isFirstLoad && outcome.newCount > 0) {
                VpnNotification.showSubscriptionUpdate(getApplication(), sub.name, outcome.newCount)
            }
        } catch (e: Exception) {
            Diagnostics.log("Subscription", e)
            _messages.tryEmit("${sub.name}: ${e.message ?: "update failed"}")
        } finally {
            _subscriptionRefreshing.update { it - sub.id }
        }
    }

    fun removeSubscription(sub: Subscription) {
        viewModelScope.launch {
            store.removeSubscription(sub.id)
            _messages.tryEmit("${sub.name} removed")
        }
    }

    fun exportSubscriptions(): String {
        val arr = org.json.JSONArray()
        subscriptions.value.forEach { sub ->
            arr.put(org.json.JSONObject().put("name", sub.name).put("url", sub.url))
        }
        return arr.toString()
    }

    fun importSubscriptions(json: String) {
        viewModelScope.launch {
            var added = 0
            try {
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url").trim()
                    val name = obj.optString("name").trim()
                    if (url.startsWith("https://")) {
                        try {
                            store.addSubscription(if (name.isBlank()) subscriptionHost(url) else name, url)
                            added++
                        } catch (_: Exception) {}
                    }
                }
                _messages.tryEmit("Imported $added subscription(s)")
            } catch (e: Exception) {
                _messages.tryEmit("Import failed: ${e.message}")
            }
        }
    }

    fun cleanDeadSubscriptionConfigs() {
        viewModelScope.launch {
            val subs = subscriptions.value
            if (subs.isEmpty()) { _messages.tryEmit("No subscriptions"); return@launch }
            var removed = 0
            subs.forEach { sub ->
                val configs = profiles.value.filter { it.subscriptionId == sub.id }
                configs.forEach { profile ->
                    val result = runCatching { PingTester.ping(profile.configText) }.getOrNull()
                    if (result == null || !result.success) {
                        store.deleteProfile(profile.id)
                        removed++
                    }
                }
            }
            _messages.tryEmit("Removed $removed dead config(s)")
        }
    }

    fun renameSubscription(sub: Subscription, name: String) {
        viewModelScope.launch {
            store.renameSubscription(sub.id, name.trim())
            _messages.tryEmit("${sub.name} renamed")
        }
    }

    fun findFastest(onResult: (TunnelProfile?) -> Unit) {
        viewModelScope.launch {
            val list = profiles.value
            if (list.isEmpty()) {
                _messages.tryEmit("Add a server first")
                onResult(null)
                return@launch
            }
            _findingFastest.value = true
            try {
                val results = coroutineScope {
                    list.take(60).map { profile ->
                        async(Dispatchers.IO) { profile to PingTester.ping(profile.configText) }
                    }.awaitAll()
                }
                results.forEach { (profile, result) ->
                    _pingResults.update { it + (profile.id to result) }
                }
                val best = results.filter { it.second.success }.minByOrNull { it.second.ms }?.first
                if (best == null) {
                    _messages.tryEmit("No reachable server found")
                } else {
                    store.setSelectedProfile(best.id)
                    _messages.tryEmit("Fastest: ${best.name} (${results.first { it.first.id == best.id }.second.ms} ms)")
                }
                onResult(best)
            } catch (e: Exception) {
                Diagnostics.log("Fastest", e)
                _messages.tryEmit("Fastest server check failed")
                onResult(null)
            } finally {
                _findingFastest.value = false
            }
        }
    }

    private fun subscriptionHost(url: String): String = try {
        URL(url).host.takeIf { it.isNotBlank() } ?: "Subscription"
    } catch (_: Exception) {
        "Subscription"
    }
}
