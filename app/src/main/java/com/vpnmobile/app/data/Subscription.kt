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
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expireAt: Long = 0L,
    val usageUpdatedAt: Long = 0L,
) {
    val usedBytes: Long get() = (upload + download).coerceAtLeast(0L)
    val hasUsage: Boolean get() = total > 0L || expireAt > 0L || usedBytes > 0L

    fun usageOrNull(): SubscriptionUsage? = if (!hasUsage) null else {
        SubscriptionUsage(upload = upload, download = download, total = total, expireAt = expireAt)
    }
}

/**
 * Traffic/expiry data reported by a panel via the `subscription-userinfo` header:
 * `upload=123; download=456; total=789; expire=1700000000`.
 */
data class SubscriptionUsage(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expireAt: Long = 0L,
) {
    val used: Long get() = (upload + download).coerceAtLeast(0L)
    val hasQuota: Boolean get() = total > 0L

    fun fractionUsed(): Float =
        if (total <= 0L) 0f else (used.toDouble() / total).toFloat().coerceIn(0f, 1f)

    fun daysLeft(now: Long = System.currentTimeMillis()): Long =
        if (expireAt <= 0L) -1L else ((expireAt - now + 86_400_000L - 1) / 86_400_000L)

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expireAt > 0L && expireAt <= now

    fun isExpiringSoon(now: Long = System.currentTimeMillis(), withinDays: Long = 3L): Boolean {
        if (expireAt <= 0L) return false
        val left = expireAt - now
        return left in 0..(withinDays * 86_400_000L)
    }

    fun isLowData(threshold: Float = 0.9f): Boolean =
        hasQuota && fractionUsed() >= threshold
}

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

    data class FetchResult(val body: String, val usage: SubscriptionUsage?)

    suspend fun fetch(url: String): String = fetchResult(url).body

    suspend fun fetchResult(url: String): FetchResult = withContext(Dispatchers.IO) {
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
            val usage = parseUsage(connection.getHeaderField("subscription-userinfo"))
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("Subscription is too large")
            FetchResult(bytes.toString(Charsets.UTF_8), usage)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseUsage(header: String?): SubscriptionUsage? {
        if (header.isNullOrBlank()) return null
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        var found = false
        header.split(';').forEach { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size != 2) return@forEach
            val key = kv[0].trim().lowercase()
            val value = kv[1].trim().toLongOrNull() ?: return@forEach
            when (key) {
                "upload" -> { upload = value.coerceAtLeast(0L); found = true }
                "download" -> { download = value.coerceAtLeast(0L); found = true }
                "total" -> { total = value.coerceAtLeast(0L); found = true }
                "expire" -> { expire = value; found = true }
            }
        }
        if (!found) return null
        val expireAt = when {
            expire <= 0L -> 0L
            expire < 100_000_000_000L -> expire * 1000L
            else -> expire
        }
        return SubscriptionUsage(upload = upload, download = download, total = total, expireAt = expireAt)
    }
}
