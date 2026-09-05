package com.vpnmobile.app.data

import java.util.Locale

object ConfigTools {

    private val allowedIpsRegex = Regex("(?m)^(AllowedIPs\\s*=\\s*).*$")

    /**
     * Forces every peer to route all IPv4/IPv6 traffic through the tunnel.
     * Combined with GoBackend's route handling this blocks traffic outside the
     * tunnel (a basic kill switch): when the tunnel drops, nothing can leak.
     */
    fun applyKillSwitch(configText: String): String =
        allowedIpsRegex.replace(configText) { "AllowedIPs = 0.0.0.0/0, ::/0" }

    fun parseEndpoint(configText: String): Pair<String, Int>? {
        val t = configText.trim()
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
