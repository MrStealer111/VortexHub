package com.vpnmobile.app.data

import android.util.Log
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object WarpGenerator {
    const val CF_PEER_PUBLIC = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    private const val REG_URL = "https://api.cloudflareclient.com/v0a1925/reg"
    private const val TAG = "WarpGenerator"

    val DEFAULT_ENDPOINTS = listOf(
        "162.159.192.1" to 2408,
        "162.159.193.1" to 2408,
        "188.114.96.1" to 2408,
        "188.114.97.1" to 2408,
        "162.159.192.1" to 500,
        "162.159.193.1" to 1701,
    )

    data class WarpResult(val config: String, val endpoint: String, val address: String)

    suspend fun generate(endpointIp: String = "162.159.192.1", port: Int = 2408, autoBest: Boolean = true, customDns: String = "1.1.1.1, 1.0.0.1"): WarpResult = withContext(Dispatchers.IO) {
        var ip = endpointIp
        var pt = port
        if (autoBest) {
            WarpScanner.findBest()?.let { best -> ip = best.first; pt = best.second }
        }
        val kp = KeyPair()
        val privB64 = kp.privateKey.toBase64()
        val pubB64 = kp.publicKey.toBase64()
        val payload = JSONObject().apply {
            put("key", pubB64)
            put("warp_enabled", true)
            put("tos", "2024-01-01T00:00:00.000Z")
            put("type", "Android")
            put("locale", "en_US")
        }.toString()

        fun doReg(urlStr: String, timeout: Int = 10000): String {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "okhttp/3.12.1")
                connectTimeout = timeout
                readTimeout = timeout
                doOutput = true
                useCaches = false
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = try { BufferedReader(InputStreamReader(conn.errorStream)).readText() } catch (_: Exception) { "" }
                throw Exception("WARP reg failed: $code $err")
            }
            return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        }
        val regEndpoints = listOf(
            REG_URL,
            "https://warp-tunnel-backup.pages.dev/api/reg",
            "https://api.cloudflareclient.com/v0a1925/reg",
        )
        var lastErr: Exception? = null
        var body = ""
        for (endpoint in regEndpoints) {
            try {
                Log.d(TAG, "WARP reg attempt: $endpoint")
                body = doReg(endpoint)
                Log.d(TAG, "WARP reg success via $endpoint")
                break
            } catch (e: Exception) {
                lastErr = e
                Log.e(TAG, "WARP reg failed on $endpoint: ${e.message}")
            }
        }
        if (body.isBlank()) {
            throw lastErr ?: Exception("WARP registration failed on all endpoints")
        }
        val json = JSONObject(body)
        val cfg = json.getJSONObject("config").getJSONObject("interface").getJSONObject("addresses")
        val v4 = cfg.optString("v4", "172.16.0.2/32")
        val v6 = cfg.optString("v6", "")
        val addrStr = if (v6.isNotBlank()) "$v4, $v6" else v4
        val reserved = (0..2).map { (0..255).random() }.joinToString(", ")
        val dns = customDns.ifBlank { "1.1.1.1, 1.0.0.1" }
        val conf = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privB64")
            appendLine("Address = $addrStr")
            appendLine("DNS = $dns")
            appendLine("Reserved = $reserved")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $CF_PEER_PUBLIC")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = $ip:$pt")
            appendLine("PersistentKeepalive = 25")
        }
        WarpResult(conf.trimEnd(), "$ip:$pt", addrStr)
    }
}
