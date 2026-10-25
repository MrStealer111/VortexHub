package com.vpnmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long = 0L,
    val createdAt: Long = 0L,
)

/** Parses common subscription payloads: plain config lists and base64-encoded lists. */
@OptIn(ExperimentalEncodingApi::class)
object SubscriptionParser {

    data class Entry(val name: String, val flag: String, val configText: String)

    fun parse(raw: String, fallbackName: String = "Subscription"): List<Entry> {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) return emptyList()

        val candidates = LinkedHashSet<String>()
        candidates += text
        if (!text.contains("://") && !text.contains("[Interface]", ignoreCase = true) && text.length > 24) {
            decodeBase64(text)?.let { candidates += it }
        }

        for (candidate in candidates) {
            val parsed = parsePayload(candidate, fallbackName)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parsePayload(text: String, fallbackName: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()

        fun add(entry: Entry) {
            if (entry.configText.isBlank()) return
            if (seen.add(entry.configText)) entries += entry
        }

        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.contains("[Interface]", ignoreCase = true)) {
            var index = 0
            normalized.split(Regex("(?im)^\\s*\\[Interface\\]\\s*$")).drop(1).forEach { body ->
                val config = "[Interface]\n" + body.trim()
                if (config.contains("[Peer]", ignoreCase = true)) {
                    index++
                    add(Entry("$fallbackName WG $index", "\u26A1", config))
                }
            }
            if (entries.isNotEmpty()) return entries
        }

        var index = 0
        normalized.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach
            val config = line.substringBefore('\u0000').trim()
            when {
                config.startsWith("ss://", ignoreCase = true) -> {
                    val parsed = ShadowsocksUri.parse(config) ?: return@forEach
                    index++
                    add(Entry(parsed.name?.takeIf { it.isNotBlank() } ?: "$fallbackName $index", "\uD83D\uDD12", config))
                }
                config.equals("warp://auto", ignoreCase = true) || config.equals("warp://", ignoreCase = true) -> {
                    index++
                    add(Entry("$fallbackName WARP $index", "\uD83C\uDF10", "warp://auto"))
                }
                config.startsWith("wireguard://", ignoreCase = true) -> {
                    index++
                    add(Entry("$fallbackName WG $index", "\u26A1", config))
                }
            }
        }
        return entries
    }

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim()
            .replace('-', '+')
            .replace('_', '/')
            .replace(Regex("\\s+"), "")
        if (normalized.isEmpty()) return null
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
}

object SubscriptionFetcher {

    private const val MAX_BYTES = 2_000_000

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw IOException("Subscription URL must start with https://")
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VortexHub/1.3")
            setRequestProperty("Accept", "text/plain, */*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("Subscription is too large")
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
