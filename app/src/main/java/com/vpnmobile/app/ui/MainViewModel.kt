package com.vpnmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vpnmobile.app.R
import com.vpnmobile.app.VpnApplication
import com.vpnmobile.app.data.PingTester
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.vpn.VpnState
import com.wireguard.android.backend.BackendException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val favorites: StateFlow<Set<String>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val connectionHistory: StateFlow<List<String>> =
        store.connectionHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                vpn.connect(profile, killSwitch.value, splitEnabled.value, splitMode.value, excludedApps.value)
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
}
