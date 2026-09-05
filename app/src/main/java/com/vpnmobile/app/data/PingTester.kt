package com.vpnmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object PingTester {
    data class Result(val ms: Int, val success: Boolean)

    suspend fun ping(configText: String, timeoutMs: Int = 3000): Result = withContext(Dispatchers.IO) {
        val endpoint = ConfigTools.parseEndpoint(configText) ?: return@withContext Result(-1, false)
        val host = endpoint.first
        val port = endpoint.second
        try {
            val elapsed = measureTimeMillis {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
            }
            Result(elapsed.toInt().coerceAtLeast(1), true)
        } catch (_: Exception) {
            try {
                val elapsed = measureTimeMillis {
                    val addr = java.net.InetAddress.getByName(host)
                    if (!addr.isReachable(timeoutMs)) throw Exception("unreachable")
                }
                Result(elapsed.toInt().coerceAtLeast(1), true)
            } catch (_: Exception) {
                Result(-1, false)
            }
        }
    }
}
