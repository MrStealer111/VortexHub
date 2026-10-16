package com.vpnmobile.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "vpn_store")

class TunnelStore(private val context: Context) {

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles_json")
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val SPLIT_ENABLED = booleanPreferencesKey("split_enabled")
        val SPLIT_EXCLUDED = stringPreferencesKey("split_excluded_apps")
        val SPLIT_MODE = stringPreferencesKey("split_mode")
        val SERVER_URL = stringPreferencesKey("server_json_url")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val WARP_RESERVED = stringPreferencesKey("warp_reserved")
        val FAVORITES = stringPreferencesKey("favorites")
        val CONNECTION_HISTORY = stringPreferencesKey("connection_history")
        val DATA_USAGE = stringPreferencesKey("data_usage")
    }

    val profiles: Flow<List<TunnelProfile>> = context.dataStore.data
        .map { prefs -> prefs[Keys.PROFILES].orEmpty().toProfiles().sortedByDescending { it.createdAt } }

    val selectedProfileId: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[Keys.SELECTED_PROFILE] }

    val autoConnect: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.AUTO_CONNECT] ?: false }

    val killSwitch: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.KILL_SWITCH] ?: false }

    val splitEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.SPLIT_ENABLED] ?: false }

    val splitMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.SPLIT_MODE] ?: "exclude" }

    val excludedApps: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.SPLIT_EXCLUDED].orEmpty()
            if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }

    val serverUrl: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.SERVER_URL].orEmpty() }

    val customDns: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.CUSTOM_DNS] ?: "1.1.1.1, 1.0.0.1" }

    val autoReconnect: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.AUTO_RECONNECT] ?: false }

    val favorites: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.FAVORITES].orEmpty()
            if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }

    val connectionHistory: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.CONNECTION_HISTORY].orEmpty()
            if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }

    val dataUsage: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.DATA_USAGE] ?: "0" }

    suspend fun addProfile(name: String, flag: String, configText: String): TunnelProfile {
        val profile = TunnelProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            flag = flag,
            configText = configText,
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES].orEmpty().toProfiles().toMutableList()
            current.add(profile)
            prefs[Keys.PROFILES] = current.toJson()
            prefs[Keys.SELECTED_PROFILE] = profile.id
        }
        return profile
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES].orEmpty().toProfiles().filterNot { it.id == id }
            prefs[Keys.PROFILES] = current.toJson()
            if (prefs[Keys.SELECTED_PROFILE] == id) prefs.remove(Keys.SELECTED_PROFILE)
        }
    }

    suspend fun setSelectedProfile(id: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_PROFILE] = id }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.KILL_SWITCH] = enabled }
    }

    suspend fun setSplitEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SPLIT_ENABLED] = enabled }
    }

    suspend fun setSplitMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SPLIT_MODE] = mode }
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.SPLIT_EXCLUDED] = apps.joinToString(",") }
    }

    suspend fun toggleExcludedApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.SPLIT_EXCLUDED].orEmpty()
            val set = if (raw.isBlank()) mutableSetOf() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            if (packageName in set) set.remove(packageName) else set.add(packageName)
            prefs[Keys.SPLIT_EXCLUDED] = set.joinToString(",")
        }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SERVER_URL] = url.trim() }
    }

    suspend fun setCustomDns(dns: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CUSTOM_DNS] = dns.trim() }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun toggleFavorite(profileId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.FAVORITES].orEmpty()
            val set = if (raw.isBlank()) mutableSetOf() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            if (profileId in set) set.remove(profileId) else set.add(profileId)
            prefs[Keys.FAVORITES] = set.joinToString(",")
        }
    }

    suspend fun addToHistory(profileId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.CONNECTION_HISTORY].orEmpty()
            val list = if (raw.isBlank()) mutableListOf() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            list.remove(profileId)
            list.add(0, profileId)
            if (list.size > 10) list.removeAt(list.lastIndex)
            prefs[Keys.CONNECTION_HISTORY] = list.joinToString(",")
        }
    }

    suspend fun updateDataUsage(bytes: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DATA_USAGE]?.toLongOrNull() ?: 0L
            prefs[Keys.DATA_USAGE] = (current + bytes).toString()
        }
    }
}
