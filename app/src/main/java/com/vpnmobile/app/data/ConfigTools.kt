package com.vpnmobile.app.data

import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ConfigTools {

    private val allowedIpsRegex = Regex("(?m)^(AllowedIPs\\s*=\\s*)(.*)$")

    /**
     * Forces every peer to route all IPv4/IPv6 traffic through the tunnel.
     * Combined with GoBackend's route handling this blocks traffic outside the
     * tunnel (a basic kill switch): when the tunnel drops, nothing can leak.
     */
    fun applyKillSwitch(configText: String): String =
        allowedIpsRegex.replace(configText) { "AllowedIPs = 0.0.0.0/0, ::/0" }

    /** Routes IPv6 into the tunnel so IPv6 traffic cannot bypass the VPN. */
    fun applyIpv6Block(configText: String): String = allowedIpsRegex.replace(configText) { match ->
        val values = match.groupValues[2].split(',').map { it.trim() }
        if (values.any { it == "::/0" }) match.value else "AllowedIPs = ${match.groupValues[2]}, ::/0"
    }

    fun isAutoWarp(configText: String): Boolean {
        val t = configText.trim()
        return t.equals("warp://auto", true) || t.equals("warp://", true)
    }

    /**
     * Accepts a WireGuard INI config, or decodes a `wireguard://` link whose payload is a
     * base64/URL-encoded INI document. Returns null when the payload is not a WireGuard config.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun normalizeWireGuard(configText: String): String? {
        val t = configText.trim()
        if (t.contains("[Interface]", ignoreCase = true)) return t
        if (!t.startsWith("wireguard://", ignoreCase = true)) return null
        val payload = t.substringAfter("://")
        val normalized = payload.trim().replace('-', '+').replace('_', '/').replace(Regex("\\s+"), "")
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            0 -> normalized
            else -> null
        }
        if (padded != null) {
            try {
                val decoded = Base64.decode(padded).toString(Charsets.UTF_8)
                if (decoded.contains("[Interface]", ignoreCase = true)) return decoded
            } catch (_: Exception) {
            }
        }
        val urlDecoded = try {
            java.net.URLDecoder.decode(payload, "UTF-8")
        } catch (_: Exception) {
            payload
        }
        return urlDecoded.takeIf { it.contains("[Interface]", ignoreCase = true) }
    }

    fun parseEndpoint(configText: String): Pair<String, Int>? {
        val t = configText.trim()
        if (isAutoWarp(t)) return "162.159.192.1" to 2408
        if (t.startsWith("ss://", true) || t.startsWith("vless://", true) || t.startsWith("vmess://", true) || t.startsWith("trojan://", true)) {
            return try {
                val withoutScheme = t.substringAfter("://")
                val atPart = withoutScheme.substringAfter("@", "")
                val hostPort = if (atPart.isNotBlank()) atPart.substringBefore("?").substringBefore("#").substringBefore("/") else withoutScheme.substringBefore("?").substringBefore("#")
                val host = hostPort.substringBefore(":").trim().removePrefix("[").removeSuffix("]")
                val portStr = hostPort.substringAfter(":", "")
                val port = portStr.substringBefore("?").substringBefore("#").toIntOrNull() ?: 443
                if (host.isBlank()) null else host to port
            } catch (_: Exception) { null }
        }
        val raw = configText.lineSequence()
            .firstOrNull { it.trimStart().startsWith("Endpoint", ignoreCase = true) }
            ?.substringAfter("=")?.trim() ?: return null
        val hostPort = raw.substringBefore(" ")
        val host = hostPort.substringBeforeLast(":").trim().removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfterLast(":", "").toIntOrNull() ?: 51820
        if (host.isBlank()) return null
        return host to port
    }

    fun protocolLabel(configText: String): String {
        val t = configText.trim().lowercase()
        return when {
            isAutoWarp(t) || t.startsWith("vless://warp@") -> "WARP"
            t.startsWith("wireguard") || t.contains("[interface]") -> "WireGuard"
            t.startsWith("ss://") -> "Shadowsocks"
            t.startsWith("vless://") -> "VLESS"
            t.startsWith("vmess://") -> "VMess"
            t.startsWith("trojan://") -> "Trojan"
            else -> "Config"
        }
    }

    fun maskIp(host: String): String {
        return try {
            if (host.contains(".")) {
                val parts = host.split(".")
                if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) "${parts[0]}.${parts[1]}.${parts[2]}.***" else "$host:***"
            } else host
        } catch (_: Exception) { host }
    }

    /** Pulls the first `Endpoint = host:port` line out of a wg-quick config for previews. */
    fun endpointPreview(configText: String): String {
        val t = configText.trim()
        if (isAutoWarp(t)) return "Cloudflare"
        if (t.startsWith("ss://", true) || t.startsWith("vless://", true) || t.startsWith("vmess://", true) || t.startsWith("trojan://", true)) {
            return try {
                val withoutScheme = t.substringAfter("://")
                val atPart = withoutScheme.substringAfter("@", "")
                val hostPort = if (atPart.isNotBlank()) atPart.substringBefore("?").substringBefore("#").substringBefore("/") else withoutScheme.substringBefore("?").substringBefore("#")
                val host = hostPort.substringBefore(":").trim()
                val masked = maskIp(host)
                if (hostPort.contains(":")) "$masked:***" else masked.ifBlank { t.take(20) + "..." }
            } catch (_: Exception) { "Hidden Endpoint" }
        }
        val ep = configText.lineSequence()
            .firstOrNull { it.trimStart().startsWith("Endpoint", ignoreCase = true) }
            ?.substringAfter("=")?.trim() ?: return "Hidden"
        return try {
            val host = ep.substringBefore(":").trim()
            val masked = maskIp(host)
            if (ep.contains(":")) "$masked:***" else masked
        } catch (_: Exception) { "Hidden" }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun formatSpeed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "0 B/s" else "${formatBytes(bytesPerSecond)}/s"

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
