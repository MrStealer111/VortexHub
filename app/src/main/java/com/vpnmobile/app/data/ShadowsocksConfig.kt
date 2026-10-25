package com.vpnmobile.app.data

import org.json.JSONObject
import java.net.URLDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class ShadowsocksConfig(
    val host: String,
    val port: Int,
    val method: String,
    val password: String,
    val name: String? = null,
) {
    fun toClientJson(): String = JSONObject()
        .put("host", host)
        .put("port", port)
        .put("method", method)
        .put("password", password)
        .toString()
}

/** Parser for SIP002 and legacy Shadowsocks URIs, including Outline `ss://` keys. */
@OptIn(ExperimentalEncodingApi::class)
object ShadowsocksUri {

    private val supportedMethods = setOf(
        "chacha20-ietf-poly1305",
        "chacha20-poly1305",
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
    )

    fun parse(uri: String): ShadowsocksConfig? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith("ss://", ignoreCase = true)) return null

        var rest = trimmed.substring(5)
        val name = rest.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::urlDecode)
        rest = rest.substringBefore('#')
        val query = rest.substringAfter('?', "").takeIf { it.isNotBlank() }
        rest = rest.substringBefore('?').trim().trimEnd('/')
        if (rest.isBlank()) return null
        if (query?.contains("plugin=", ignoreCase = true) == true) return null

        val credentials: String
        val hostPort: String
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            credentials = decodeCredentials(rest.substring(0, at)) ?: return null
            hostPort = rest.substring(at + 1)
        } else {
            val decoded = decodeBase64(rest) ?: return null
            val decodedAt = decoded.lastIndexOf('@')
            if (decodedAt <= 0) return null
            credentials = decoded.substring(0, decodedAt)
            hostPort = decoded.substring(decodedAt + 1)
        }

        val separator = credentials.indexOf(':')
        if (separator <= 0) return null
        val method = credentials.substring(0, separator).trim().lowercase()
        val password = credentials.substring(separator + 1)
        if (method !in supportedMethods || password.isEmpty()) return null

        val (host, port) = splitHostPort(hostPort) ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return ShadowsocksConfig(host, port, method, password, name)
    }

    fun isShadowsocks(uri: String): Boolean = uri.trim().startsWith("ss://", ignoreCase = true)

    private fun decodeCredentials(raw: String): String? {
        if (raw.contains(':')) return urlDecode(raw)
        decodeBase64(raw)?.let { return it }
        val unescaped = urlDecode(raw)
        return unescaped.takeIf { it.contains(':') }
    }

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim().replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            0 -> normalized
            else -> return null
        }
        return try {
            Base64.decode(padded).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun splitHostPort(value: String): Pair<String, Int>? {
        val hostPort = value.trim().trimEnd('/')
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end <= 1 || end + 1 >= hostPort.length || hostPort[end + 1] != ':') return null
            val host = hostPort.substring(1, end)
            val port = hostPort.substring(end + 2).toIntOrNull() ?: return null
            return host to port
        }
        val colon = hostPort.lastIndexOf(':')
        if (colon <= 0) return null
        val host = hostPort.substring(0, colon)
        val port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun urlDecode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
