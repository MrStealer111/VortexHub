package com.vpnmobile.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ServerApi {

    data class ServerEntry(val name: String, val flag: String, val uri: String, val premium: Boolean)

    suspend fun fetchFree(context: Context, customUrl: String = ""): List<ServerEntry> = withContext(Dispatchers.IO) {
        val source = (if (customUrl.isNotBlank()) tryFetch(customUrl) else null) ?: loadAsset(context)
        parse(source).filter { isSupported(it.uri) }
    }

    private fun isSupported(uri: String): Boolean {
        val t = uri.trim().lowercase()
        return t.startsWith("warp://") || t.startsWith("wireguard://") || t.contains("[interface]") ||
            (t.startsWith("ss://") && ShadowsocksUri.parse(uri) != null)
    }

    private fun tryFetch(urlStr: String): String? = try {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 4000; readTimeout = 4000; requestMethod = "GET" }
        if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText() else null
    } catch (_: Exception) { null }

    private fun loadAsset(context: Context): String = try {
        context.assets.open("server.json").bufferedReader().readText()
    } catch (_: Exception) { """{"free":[]}""" }

    private fun parse(jsonStr: String): List<ServerEntry> = try {
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("free") ?: return emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ServerEntry(o.getString("name"), o.optString("flag", "🌐"), o.getString("uri"), o.optBoolean("premium", false))
        }
    } catch (_: Exception) { emptyList() }
}
