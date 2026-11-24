package com.vpnmobile.app.data

import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts VLESS / VMess / Trojan share links into an Xray-core JSON config with a
 * TUN inbound. The TUN fd is supplied at runtime by XrayVpnService.
 */
@OptIn(ExperimentalEncodingApi::class)
object XrayConfig {

    private data class UriParts(
        val scheme: String,
        val userInfo: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String?,
    )

    private val uuidRegex = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    private val uuidNoDashRegex = Regex("^[0-9a-fA-F]{32}$")

    fun isXrayUri(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("vless://warp@", ignoreCase = true)) return false
        return t.startsWith("vless://", ignoreCase = true) ||
            t.startsWith("vmess://", ignoreCase = true) ||
            t.startsWith("trojan://", ignoreCase = true)
    }

    fun protocolName(text: String): String? = when {
        text.trim().startsWith("vless://", ignoreCase = true) -> "VLESS"
        text.trim().startsWith("vmess://", ignoreCase = true) -> "VMess"
        text.trim().startsWith("trojan://", ignoreCase = true) -> "Trojan"
        else -> null
    }

    fun displayName(text: String): String? = try {
        val t = text.trim()
        when {
            t.startsWith("vmess://", ignoreCase = true) ->
                vmessJson(t).optString("ps").takeIf { it.isNotBlank() }
            else -> parseUri(t).fragment?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    fun parseEndpoint(text: String): Pair<String, Int>? = try {
        val t = text.trim()
        when {
            t.startsWith("vmess://", ignoreCase = true) -> {
                val o = vmessJson(t)
                val host = o.optString("add").trim()
                val port = o.opt("port")?.toString()?.toIntOrNull() ?: 443
                if (host.isBlank()) null else host to port
            }
            else -> parseUri(t).let { it.host to it.port }
        }
    } catch (_: Exception) {
        null
    }

    fun build(text: String): String {
        val t = text.trim()
        require(t.isNotEmpty()) { "Configuration is empty" }
        val outbound = when {
            t.startsWith("vless://", ignoreCase = true) -> vlessOutbound(t)
            t.startsWith("vmess://", ignoreCase = true) -> vmessOutbound(t)
            t.startsWith("trojan://", ignoreCase = true) -> trojanOutbound(t)
            else -> throw IllegalArgumentException("Unsupported link — expected vless://, vmess:// or trojan://")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning").put("access", "none"))
        root.put(
            "dns",
            JSONObject()
                .put("tag", "dns-out")
                .put("servers", JSONArray().put("https://1.1.1.1/dns-query").put("https://8.8.8.8/dns-query"))
        )
        root.put(
            "inbounds",
            JSONArray()
                .put(
                    JSONObject()
                        .put("tag", "tun-in")
                        .put("protocol", "tun")
                        .put("settings", JSONObject().put("name", "vortexhub").put("MTU", 1500))
                )
                .put(
                    JSONObject()
                        .put("tag", "socks-in")
                        .put("protocol", "socks")
                        .put("listen", "127.0.0.1")
                        .put("port", 10808)
                        .put("settings", JSONObject().put("auth", "noauth"))
                )
        )
        root.put(
            "routing",
            JSONObject().put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", JSONArray().put("tun-in"))
                        .put("network", "udp")
                        .put("port", "53")
                        .put("outboundTag", "dns-out")
                )
            )
        )
        root.put("outbounds", JSONArray().put(outbound))
        return root.toString()
    }

    private fun vlessOutbound(text: String): JSONObject {
        val u = parseUri(text)
        require(u.scheme == "vless") { "Not a VLESS link" }
        val id = u.userInfo.trim().removeSurrounding("{", "}")
        require(id.isNotBlank()) { "VLESS UUID is missing" }
        require(uuidRegex.matches(id) || uuidNoDashRegex.matches(id)) { "Invalid VLESS UUID: $id" }

        val q = u.query
        val user = JSONObject()
            .put("id", id)
            .put("encryption", q["encryption"]?.takeIf { it.isNotBlank() } ?: "none")
        val secure = (q["security"] ?: q["tls"] ?: "").lowercase()
        if (secure !in setOf("", "none")) {
            q["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        }

        val vnext = JSONObject()
            .put("address", u.host)
            .put("port", u.port)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings(q, u.host, defaultTls = false))
    }

    private fun vmessOutbound(text: String): JSONObject {
        val o = vmessJson(text)
        val host = o.optString("add").trim()
        require(host.isNotBlank()) { "VMess server address (add) is missing" }
        val port = o.opt("port")?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid VMess port")
        require(port in 1..65535) { "Invalid VMess port: $port" }
        val id = o.optString("id").trim()
        require(id.isNotBlank()) { "VMess UUID (id) is missing" }
        val aid = o.opt("aid")?.toString()?.toIntOrNull() ?: 0
        val scy = o.optString("scy").ifBlank { "auto" }

        val user = JSONObject()
            .put("id", id)
            .put("alterId", aid)
            .put("security", scy)
        val vnext = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        val q = mutableMapOf<String, String>()
        o.optString("net").takeIf { it.isNotBlank() }?.let { q["type"] = it }
        o.optString("type").takeIf { it.isNotBlank() }?.let { q["headertype"] = it }
        o.optString("host").takeIf { it.isNotBlank() }?.let { q["host"] = it }
        o.optString("path").takeIf { it.isNotBlank() }?.let { q["path"] = it }
        o.optString("tls").takeIf { it.isNotBlank() }?.let { q["security"] = it }
        o.optString("sni").takeIf { it.isNotBlank() }?.let { q["sni"] = it }
        stringList(o.opt("alpn"))?.let { q["alpn"] = it }
        o.optString("fp").takeIf { it.isNotBlank() }?.let { q["fp"] = it }
        o.optString("pbk").takeIf { it.isNotBlank() }?.let { q["pbk"] = it }
        o.optString("sid").takeIf { it.isNotBlank() }?.let { q["sid"] = it }
        o.optString("spx").takeIf { it.isNotBlank() }?.let { q["spx"] = it }
        o.optString("serviceName").takeIf { it.isNotBlank() }?.let { q["servicename"] = it }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings(q, host, defaultTls = false))
    }

    private fun trojanOutbound(text: String): JSONObject {
        val u = parseUri(text)
        require(u.scheme == "trojan") { "Not a Trojan link" }
        require(u.userInfo.isNotBlank()) { "Trojan password is missing" }

        val server = JSONObject()
            .put("address", u.host)
            .put("port", u.port)
            .put("password", u.userInfo)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings(u.query, u.host, defaultTls = true))
    }

    private fun streamSettings(q: Map<String, String>, host: String, defaultTls: Boolean): JSONObject {
        val stream = JSONObject()
        val networkRaw = (q["type"] ?: q["net"] ?: "tcp").lowercase()
        val network = when (networkRaw) {
            "tcp", "raw" -> "tcp"
            "ws", "websocket" -> "ws"
            "grpc", "gun" -> "grpc"
            "h2", "http" -> "h2"
            "httpupgrade" -> "httpupgrade"
            "xhttp", "splithttp" -> "xhttp"
            "kcp", "mkcp" -> "kcp"
            "quic" -> "quic"
            else -> throw IllegalArgumentException("Unsupported transport: $networkRaw")
        }
        stream.put("network", network)

        val path = q["path"]?.takeIf { it.isNotBlank() } ?: "/"
        val headerHost = q["host"]?.takeIf { it.isNotBlank() } ?: host

        when (network) {
            "tcp" -> {
                val headerType = (q["headertype"] ?: "none").lowercase()
                if (headerType == "http") {
                    val request = JSONObject()
                        .put("path", JSONArray().put(path))
                        .put("headers", JSONObject().put("Host", JSONArray().put(headerHost)))
                    stream.put(
                        "tcpSettings",
                        JSONObject().put("header", JSONObject().put("type", "http").put("request", request))
                    )
                }
            }
            "ws" -> stream.put(
                "wsSettings",
                JSONObject().put("path", path).put("headers", JSONObject().put("Host", headerHost))
            )
            "grpc" -> {
                val service = q["servicename"]?.takeIf { it.isNotBlank() }
                    ?: q["path"]?.trim('/')?.takeIf { it.isNotBlank() }
                    ?: ""
                val grpc = JSONObject().put("serviceName", service)
                if (q["mode"]?.equals("multi", ignoreCase = true) == true) grpc.put("multiMode", true)
                stream.put("grpcSettings", grpc)
            }
            "h2" -> stream.put(
                "httpSettings",
                JSONObject().put("path", path).put("host", JSONArray().put(headerHost))
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject().put("path", path).put("host", headerHost)
            )
            "xhttp" -> {
                val xhttp = JSONObject().put("path", path).put("host", headerHost)
                q["mode"]?.takeIf { it.isNotBlank() }?.let { xhttp.put("mode", it) }
                stream.put("xhttpSettings", xhttp)
            }
            "kcp" -> {
                val kcp = JSONObject().put(
                    "header",
                    JSONObject().put("type", (q["headertype"] ?: "none").lowercase())
                )
                q["seed"]?.takeIf { it.isNotBlank() }?.let { kcp.put("seed", it) }
                stream.put("kcpSettings", kcp)
            }
            "quic" -> stream.put(
                "quicSettings",
                JSONObject()
                    .put("security", q["quicsecurity"] ?: "none")
                    .put("key", q["key"] ?: "")
                    .put("header", JSONObject().put("type", (q["headertype"] ?: "none").lowercase()))
            )
        }

        val security = (q["security"] ?: q["tls"] ?: "").lowercase().ifBlank {
            if (defaultTls) "tls" else "none"
        }
        when (security) {
            "tls", "xtls" -> {
                val tls = JSONObject()
                val sni = q["sni"] ?: q["peer"] ?: q["host"] ?: host
                if (sni.isNotBlank()) tls.put("serverName", sni)
                if (q.bool("allowinsecure", "insecure")) tls.put("allowInsecure", true)
                alpn(q["alpn"])?.let { tls.put("alpn", it) }
                (q["fp"] ?: q["fingerprint"])?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                stream.put("security", "tls").put("tlsSettings", tls)
            }
            "reality" -> {
                val publicKey = q["pbk"] ?: q["publickey"]
                require(!publicKey.isNullOrBlank()) { "REALITY public key (pbk) is missing" }
                val reality = JSONObject().put("publicKey", publicKey)
                val sni = q["sni"] ?: q["peer"] ?: host
                if (sni.isNotBlank()) reality.put("serverName", sni)
                reality.put("fingerprint", q["fp"] ?: q["fingerprint"] ?: "chrome")
                q["sid"]?.takeIf { it.isNotBlank() }?.let { reality.put("shortId", it) }
                q["spx"]?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
                stream.put("security", "reality").put("realitySettings", reality)
            }
            "none", "" -> stream.put("security", "none")
            else -> throw IllegalArgumentException("Unsupported security: $security")
        }
        return stream
    }

    private fun parseUri(text: String): UriParts {
        val scheme = text.substringBefore("://", "").lowercase()
        require(scheme == "vless" || scheme == "trojan") { "Unsupported link scheme" }
        var rest = text.substringAfter("://")

        val fragment = if (rest.contains("#")) {
            rest.substringAfter("#").let(::decode).takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (rest.contains("#")) rest = rest.substringBefore("#")
        val query = if (rest.contains("?")) parseQuery(rest.substringAfter("?")) else emptyMap()
        if (rest.contains("?")) rest = rest.substringBefore("?")

        val authority = rest.substringBefore("/")
        val at = authority.lastIndexOf('@')
        require(at > 0) { "Missing credentials in $scheme link" }
        val userInfo = decode(authority.substring(0, at))
        val hostPort = authority.substring(at + 1).trim()
        require(hostPort.isNotBlank()) { "Server address is missing" }

        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            require(end > 0) { "Invalid IPv6 address" }
            host = hostPort.substring(1, end)
            port = hostPort.substring(end + 1).removePrefix(":").toIntOrNull() ?: 443
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon > 0) {
                host = hostPort.substring(0, colon)
                port = hostPort.substring(colon + 1).toIntOrNull() ?: 443
            } else {
                host = hostPort
                port = 443
            }
        }
        require(host.isNotBlank()) { "Server address is missing" }
        require(port in 1..65535) { "Invalid server port: $port" }
        return UriParts(scheme, userInfo, host, port, query, fragment)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        raw.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val key = decode(part.substringBefore('=')).trim().lowercase()
            val value = if (part.contains('=')) decode(part.substringAfter('=')) else ""
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }

    private fun vmessJson(text: String): JSONObject {
        var payload = text.trim().removePrefix("vmess://")
        val fragment = if (payload.contains("#")) {
            payload.substringAfter("#").let(::decode).takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (payload.contains("#")) payload = payload.substringBefore("#")
        payload = payload.trim()
        val decoded = decodeBase64(payload)
            ?: throw IllegalArgumentException("Invalid VMess link — expected base64 JSON")
        val obj = try {
            JSONObject(decoded)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid VMess JSON: ${e.message}")
        }
        if (fragment != null && obj.optString("ps").isBlank()) obj.put("ps", fragment)
        return obj
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

    private fun decode(value: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val char = value[i]
            if (char == '%' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    i += 3
                    continue
                }
            }
            val encoded = char.toString().toByteArray(Charsets.UTF_8)
            bytes.write(encoded, 0, encoded.size)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun stringList(value: Any?): String? = when (value) {
        null -> null
        is JSONArray -> (0 until value.length())
            .mapNotNull { value.optString(it).takeIf { s -> s.isNotBlank() } }
            .joinToString(",")
        else -> value.toString().takeIf { it.isNotBlank() }
    }

    private fun alpn(value: String?): JSONArray? {
        if (value.isNullOrBlank()) return null
        val items = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (items.isEmpty()) null else JSONArray(items)
    }

    private fun Map<String, String>.bool(vararg keys: String): Boolean = keys.any { key ->
        val value = this[key] ?: return@any false
        value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
    }
}
