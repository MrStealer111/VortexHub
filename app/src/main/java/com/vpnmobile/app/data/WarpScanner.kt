package com.vpnmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import kotlin.system.measureTimeMillis

object WarpScanner {
    val CF_RANGES = listOf("162.159.192.0/24","162.159.193.0/24","162.159.195.0/24","188.114.96.0/24","188.114.97.0/24")
    val CF_PORTS = listOf(500, 2408, 1701, 4500)

    private fun hosts(cidr: String): List<String> {
        val parts = cidr.split("/")
        val base = parts[0].split(".").map { it.toInt() }
        val prefix = parts[1].toInt()
        val total = 1 shl (32 - prefix)
        val baseInt = (base[0] shl 24) or (base[1] shl 16) or (base[2] shl 8) or base[3]
        return (1 until total-1).map { i ->
            val ip = baseInt + i
            "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
        }
    }

    private fun probe(ip: String, timeout: Int): Long? = try {
        val t = measureTimeMillis {
            Socket().use { s -> s.connect(InetSocketAddress(ip, 443), timeout) }
        }
        t
    } catch (_: Exception) { null }

    suspend fun findBest(timeout: Int = 1500, maxIps: Int = 40): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val all = CF_RANGES.flatMap { hosts(it) }.shuffled().take(maxIps)
        val results = all.map { ip ->
            async {
                val lat = probe(ip, timeout)
                if (lat != null) Triple(ip, 2408, lat) else null
            }
        }.awaitAll().filterNotNull().sortedBy { it.third }
        results.firstOrNull()?.let { it.first to it.second }
    }
}
