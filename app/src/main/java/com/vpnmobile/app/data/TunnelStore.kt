package com.vpnmobile.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
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
        val SUBSCRIPTIONS = stringPreferencesKey("subscriptions_json")
        val BLOCK_IPV6 = booleanPreferencesKey("block_ipv6")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val HIDE_KEYS = booleanPreferencesKey("hide_keys")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val LANGUAGE = stringPreferencesKey("language")
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

    val subscriptions: Flow<List<Subscription>> = context.dataStore.data
        .map { prefs -> prefs[Keys.SUBSCRIPTIONS].orEmpty().toSubscriptions() }

    val blockIpv6: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.BLOCK_IPV6] ?: true }

    val biometricLock: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.BIOMETRIC_LOCK] ?: false }

    val secureScreen: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.SECURE_SCREEN] ?: false }

    val hideKeys: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.HIDE_KEYS] ?: true }

    val themeMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.THEME_MODE] ?: "system" }

    val accentColor: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.ACCENT_COLOR] ?: "orange" }

    val language: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.LANGUAGE] ?: "system" }

    suspend fun addProfile(
        name: String,
        flag: String,
        configText: String,
        subscriptionId: String = "",
    ): TunnelProfile {
        val profile = TunnelProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            flag = flag,
            configText = configText,
            createdAt = System.currentTimeMillis(),
            subscriptionId = subscriptionId,
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

    suspend fun setBlockIpv6(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BLOCK_IPV6] = enabled }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setSecureScreen(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SECURE_SCREEN] = enabled }
    }

    suspend fun setHideKeys(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.HIDE_KEYS] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode }
    }

    suspend fun setAccentColor(color: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACCENT_COLOR] = color }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[Keys.LANGUAGE] = language }
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

    suspend fun addSubscription(name: String, url: String): Subscription {
        val sub = Subscription(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SUBSCRIPTIONS].orEmpty().toSubscriptions().toMutableList()
            current.add(sub)
            prefs[Keys.SUBSCRIPTIONS] = current.toSubscriptionsJson()
        }
        return sub
    }

    suspend fun removeSubscription(id: String) {
        context.dataStore.edit { prefs ->
            val subscriptions = prefs[Keys.SUBSCRIPTIONS].orEmpty().toSubscriptions()
                .filterNot { it.id == id }
            prefs[Keys.SUBSCRIPTIONS] = subscriptions.toSubscriptionsJson()

            val profiles = prefs[Keys.PROFILES].orEmpty().toProfiles()
            val removed = profiles.filter { it.subscriptionId == id }.map { it.id }.toSet()
            val remaining = profiles.filterNot { it.subscriptionId == id }
            prefs[Keys.PROFILES] = remaining.toJson()
            if (prefs[Keys.SELECTED_PROFILE].orEmpty() in removed) {
                remaining.lastOrNull()?.let { prefs[Keys.SELECTED_PROFILE] = it.id }
                    ?: prefs.remove(Keys.SELECTED_PROFILE)
            }
        }
    }

    suspend fun replaceSubscriptionProfiles(
        subscriptionId: String,
        entries: List<SubscriptionParser.Entry>,
    ) {
        context.dataStore.edit { prefs ->
            val profiles = prefs[Keys.PROFILES].orEmpty().toProfiles()
            val removedIds = profiles.filter { it.subscriptionId == subscriptionId }.map { it.id }.toSet()
            val kept = profiles.filterNot { it.subscriptionId == subscriptionId }
            val added = entries.map { entry ->
                TunnelProfile(
                    id = UUID.randomUUID().toString(),
                    name = entry.name,
                    flag = entry.flag,
                    configText = entry.configText,
                    createdAt = System.currentTimeMillis(),
                    subscriptionId = subscriptionId,
                )
            }
            prefs[Keys.PROFILES] = (kept + added).toJson()

            val selected = prefs[Keys.SELECTED_PROFILE]
            if (selected.isNullOrBlank() || selected in removedIds) {
                added.firstOrNull()?.let { prefs[Keys.SELECTED_PROFILE] = it.id }
            }

            val subscriptions = prefs[Keys.SUBSCRIPTIONS].orEmpty().toSubscriptions().map {
                if (it.id == subscriptionId) it.copy(lastUpdated = System.currentTimeMillis()) else it
            }
            prefs[Keys.SUBSCRIPTIONS] = subscriptions.toSubscriptionsJson()
        }
    }
}

private fun List<Subscription>.toSubscriptionsJson(): String {
    val array = JSONArray()
    forEach { sub ->
        array.put(
            JSONObject()
                .put("id", sub.id)
                .put("name", sub.name)
                .put("url", sub.url)
                .put("lastUpdated", sub.lastUpdated)
                .put("createdAt", sub.createdAt)
        )
    }
    return array.toString()
}

private fun String.toSubscriptions(): List<Subscription> = try {
    val array = JSONArray(this)
    (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        Subscription(
            id = obj.getString("id"),
            name = obj.optString("name", "Subscription"),
            url = obj.getString("url"),
            lastUpdated = obj.optLong("lastUpdated", 0L),
            createdAt = obj.optLong("createdAt", 0L),
        )
    }.sortedBy { it.createdAt }
} catch (_: Exception) {
    emptyList()
}
