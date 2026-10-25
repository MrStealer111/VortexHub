package com.vpnmobile.app.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

object DnsCheck {

    data class Result(
        val egressIp: String?,
        val resolverIp: String?,
        val dnsServers: String?,
        val error: String? = null,
    )

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val egress = runCatching { fetch("https://api.ipify.org") }.getOrNull()
        val resolver = runCatching {
            InetAddress.getByName("whoami.ds.akahelp.net").hostAddress
        }.getOrNull()
        val dns = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val props = cm?.getLinkProperties(cm.activeNetwork)
            props?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ")
        }.getOrNull()
        Result(
            egressIp = egress,
            resolverIp = resolver,
            dnsServers = dns,
            error = if (egress == null && resolver == null) "Network check failed" else null,
        )
    }

    private fun fetch(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readLine()?.trim() }
        } finally {
            conn.disconnect()
        }
    }
}
