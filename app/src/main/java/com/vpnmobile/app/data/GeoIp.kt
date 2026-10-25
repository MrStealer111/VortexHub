package com.vpnmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Resolves the public egress IP to a country/city and a flag emoji. */
object GeoIp {

    data class Info(
        val ip: String?,
        val country: String?,
        val countryCode: String?,
        val city: String?,
        val flag: String,
    ) {
        val label: String
            get() = when {
                !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
                !country.isNullOrBlank() -> country
                else -> ip ?: "Unknown location"
            }
    }

    private const val CACHE_MS = 10 * 60 * 1000L
    private var cachedAt = 0L
    private var cached: Info? = null

    suspend fun lookup(force: Boolean = false): Info? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cachedAt < CACHE_MS) return@withContext cached
        val info = runCatching { fetchGeo() }.getOrNull()
            ?: runCatching { fetchIpOnly() }.getOrNull()
        if (info != null) {
            cached = info
            cachedAt = now
        }
        info
    }

    fun flagFor(countryCode: String?): String {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "\uD83C\uDF10"
        return buildString {
            code.forEach { char ->
                appendCodePoint(0x1F1E6 + (char - 'A'))
            }
        }
    }

    private fun fetchGeo(): Info {
        val json = get("https://ipwho.is/") ?: error("empty response")
        val obj = JSONObject(json)
        if (!obj.optBoolean("success", false)) error("lookup failed")
        val countryCode = obj.optString("country_code").takeIf { it.isNotBlank() }
        return Info(
            ip = obj.optString("ip").takeIf { it.isNotBlank() },
            country = obj.optString("country").takeIf { it.isNotBlank() },
            countryCode = countryCode,
            city = obj.optString("city").takeIf { it.isNotBlank() },
            flag = flagFor(countryCode),
        )
    }

    private fun fetchIpOnly(): Info {
        val ip = get("https://api.ipify.org")?.trim()?.takeIf { it.isNotBlank() }
        return Info(ip = ip, country = null, countryCode = null, city = null, flag = "\uD83C\uDF10")
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
