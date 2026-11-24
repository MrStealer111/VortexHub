package com.vpnmobile.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
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
    private const val SOCKS_HOST = "127.0.0.1"
    private const val SOCKS_PORT = 10808
    private var cachedAt = 0L
    private var cached: Info? = null

    suspend fun lookup(context: Context, force: Boolean = false): Info? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cachedAt < CACHE_MS) return@withContext cached
        val proxy = if (hasVpn(context)) socksProxy() else null
        val info = runCatching { fetchGeo(proxy) }.getOrNull()
            ?: runCatching { fetchIpOnly(proxy) }.getOrNull()
        if (info != null) {
            cached = info
            cachedAt = now
        }
        info
    }

    fun clearCache() {
        cached = null
        cachedAt = 0L
    }

    fun flagFor(countryCode: String?): String {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "\uD83C\uDF10"
        return buildString {
            code.forEach { char -> appendCodePoint(0x1F1E6 + (char - 'A')) }
        }
    }

    private fun hasVpn(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.allNetworks.any { network ->
            val caps = manager.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun socksProxy(): Proxy? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), 400)
        }
        Proxy(Proxy.Type.SOCKS, InetSocketAddress(SOCKS_HOST, SOCKS_PORT))
    } catch (_: Exception) {
        null
    }

    private fun fetchGeo(proxy: Proxy?): Info {
        val json = get("https://ipwho.is/", proxy) ?: error("empty response")
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

    private fun fetchIpOnly(proxy: Proxy?): Info {
        val ip = get("https://api.ipify.org", proxy)?.trim()?.takeIf { it.isNotBlank() }
        return Info(ip = ip, country = null, countryCode = null, city = null, flag = "\uD83C\uDF10")
    }

    private fun get(url: String, proxy: Proxy?): String? {
        val connection = (if (proxy == null) URL(url).openConnection() else URL(url).openConnection(proxy))
            as HttpURLConnection
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
