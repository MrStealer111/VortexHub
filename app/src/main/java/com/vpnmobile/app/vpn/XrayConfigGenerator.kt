package com.vpnmobile.app.vpn

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object XrayConfigGenerator {

    private const val TAG = "XrayConfigGen"
    private const val LOCAL_SOCKS_PORT = 10808
    private const val LOCAL_HTTP_PORT = 10809

    fun generateConfig(uri: String, customDns: String = "1.1.1.1"): String {
        val t = uri.trim()
        return when {
            t.startsWith("ss://", true) -> generateSS(t, customDns)
            t.startsWith("vless://", true) -> generateVless(t, customDns)
            t.startsWith("vmess://", true) -> generateVmess(t, customDns)
            t.startsWith("trojan://", true) -> generateTrojan(t, customDns)
            else -> throw IllegalArgumentException("Unsupported protocol: $t")
        }
    }

    private fun generateSS(uri: String, dns: String): String {
        val raw = uri.removePrefix("ss://")
        val (serverPart, fragment) = extractFragment(raw)
        val (userinfo, hostPort) = splitAtSign(serverPart)
        val (host, port) = parseHostPort(hostPort)

        val password: String
        val method: String
        if (userinfo != null) {
            val decoded = try { base64Decode(userinfo) } catch (_: Exception) { userinfo }
            val parts = decoded.split(":", limit = 2)
            method = parts.getOrElse(0) { "aes-256-gcm" }
            password = parts.getOrElse(1) { "" }
        } else {
            method = "aes-256-gcm"
            password = ""
        }

        val name = fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "$host:$port"

        return buildXrayConfig(
            protocol = "shadowsocks",
            serverHost = host,
            serverPort = port,
            name = name,
            dns = dns,
            extraOutboundParams = JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("method", method)
                    put("password", password)
                }))
            },
            streamSettings = JSONObject()
        )
    }

    private fun generateVless(uri: String, dns: String): String {
        val raw = uri.removePrefix("vless://")
        val (serverPart, fragment) = extractFragment(raw)
        val (userInfo, hostPort) = splitAtSign(serverPart)
        val (host, port) = parseHostPort(hostPort)

        if (userInfo == null || userInfo.isEmpty()) throw IllegalArgumentException("VLESS requires UUID")

        val uuid = userInfo
        val name = fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "$host:$port"

        val queryParams = parseQueryParams(hostPort.substringAfter("?", ""))

        val flow = queryParams["flow"] ?: ""
        val security = queryParams["security"] ?: "none"
        val sni = queryParams["sni"] ?: queryParams["host"] ?: host
        val network = queryParams["type"] ?: "tcp"
        val serviceName = queryParams["serviceName"] ?: ""
        val path = queryParams["path"] ?: ""
        val hostHeader = queryParams["host"] ?: host
        val alpn = queryParams["alpn"] ?: ""
        val fp = queryParams["fp"] ?: ""
        val pbk = queryParams["pbk"] ?: ""
        val sid = queryParams["sid"] ?: ""
        val spx = queryParams["spx"] ?: ""

        val userObj = JSONObject().apply {
            put("id", uuid)
            put("encryption", "none")
            if (flow.isNotEmpty()) put("flow", flow)
        }

        val streamSettings = buildStreamSettings(
            network = network,
            security = security,
            sni = sni,
            host = hostHeader,
            path = path,
            serviceName = serviceName,
            alpn = alpn,
            fp = fp,
            pbk = pbk,
            sid = sid,
            spx = spx
        )

        return buildXrayConfig(
            protocol = "vless",
            serverHost = host,
            serverPort = port,
            name = name,
            dns = dns,
            extraOutboundParams = JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("users", JSONArray().put(userObj))
                }))
            },
            streamSettings = streamSettings
        )
    }

    private fun generateVmess(uri: String, dns: String): String {
        val raw = uri.removePrefix("vmess://")
        val decoded = try { base64Decode(raw) } catch (_: Exception) { raw }
        val json = JSONObject(decoded)

        val host = json.optString("add", json.optString("host", ""))
        val port = json.optInt("port", 443)
        val uuid = json.optString("id", "")
        val aid = json.optInt("aid", 0)
        val net = json.optString("net", "tcp")
        val type_ = json.optString("type", "none")
        val host_ = json.optString("host", "")
        val path = json.optString("path", "")
        val tls = json.optString("tls", "")
        val sni = json.optString("sni", "")
        val alpn = json.optString("alpn", "")
        val fp = json.optString("fp", "")
        val name = json.optString("ps", "$host:$port")

        val userObj = JSONObject().apply {
            put("id", uuid)
            put("alterId", aid)
            put("security", "auto")
        }

        val streamSettings = buildStreamSettings(
            network = net,
            security = if (tls == "tls") "tls" else "none",
            sni = sni.ifEmpty { host },
            host = host_,
            path = path,
            serviceName = "",
            alpn = alpn,
            fp = fp
        )

        return buildXrayConfig(
            protocol = "vmess",
            serverHost = host,
            serverPort = port,
            name = name,
            dns = dns,
            extraOutboundParams = JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("users", JSONArray().put(userObj))
                }))
            },
            streamSettings = streamSettings
        )
    }

    private fun generateTrojan(uri: String, dns: String): String {
        val raw = uri.removePrefix("trojan://")
        val (serverPart, fragment) = extractFragment(raw)
        val (password, hostPort) = splitAtSign(serverPart)
        val (host, port) = parseHostPort(hostPort)

        val name = fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "$host:$port"

        val queryParams = parseQueryParams(hostPort.substringAfter("?", ""))

        val security = queryParams["security"] ?: "tls"
        val sni = queryParams["sni"] ?: host
        val fp = queryParams["fp"] ?: ""
        val alpn = queryParams["alpn"] ?: ""
        val network = queryParams["type"] ?: "tcp"

        val userObj = JSONObject().apply {
            put("password", password ?: "")
        }

        val streamSettings = buildStreamSettings(
            network = network,
            security = security,
            sni = sni,
            host = sni,
            path = "",
            serviceName = "",
            alpn = alpn,
            fp = fp
        )

        return buildXrayConfig(
            protocol = "trojan",
            serverHost = host,
            serverPort = port,
            name = name,
            dns = dns,
            extraOutboundParams = JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("password", password ?: "")
                }))
            },
            streamSettings = streamSettings
        )
    }

    private fun buildXrayConfig(
        protocol: String,
        serverHost: String,
        serverPort: Int,
        name: String,
        dns: String,
        extraOutboundParams: JSONObject,
        streamSettings: JSONObject
    ): String {
        val socksInbound = JSONObject().apply {
            put("port", LOCAL_SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls"))
            })
            put("tag", "socks-in")
        }

        val httpInbound = JSONObject().apply {
            put("port", LOCAL_HTTP_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject())
            put("tag", "http-in")
        }

        val tunInbound = JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "dns")
            put("settings", JSONObject().apply {
                put("network", "tcp,udp")
                put("address", "127.0.0.1")
                put("port", 53)
            })
        }

        val outbound = JSONObject().apply {
            put("protocol", protocol)
            put("settings", extraOutboundParams)
            put("streamSettings", streamSettings)
            put("tag", "proxy")
        }

        val directOutbound = JSONObject().apply {
            put("protocol", "freedom")
            put("tag", "direct")
        }

        val blockOutbound = JSONObject().apply {
            put("protocol", "blackhole")
            put("tag", "block")
        }

        val routing = JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("inboundTag", JSONArray().put("tun-in"))
                    put("outboundTag", "proxy")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
            })
        }

        val config = JSONObject().apply {
            put("log", JSONObject().apply {
                put("loglevel", "warning")
            })
            put("dns", JSONObject().apply {
                put("servers", JSONArray().put(dns))
                put("queryStrategy", "UseIP")
            })
            put("inbounds", JSONArray().put(socksInbound).put(httpInbound).put(tunInbound))
            put("outbounds", JSONArray().put(outbound).put(directOutbound).put(blockOutbound))
            put("routing", routing)
        }

        return config.toString(2)
    }

    private fun buildStreamSettings(
        network: String,
        security: String,
        sni: String,
        host: String,
        path: String,
        serviceName: String,
        alpn: String,
        fp: String,
        pbk: String = "",
        sid: String = "",
        spx: String = ""
    ): JSONObject {
        return JSONObject().apply {
            put("network", network)

            if (security == "tls") {
                put("security", "tls")
                put("tlsSettings", JSONObject().apply {
                    if (sni.isNotEmpty()) put("serverName", sni)
                    if (fp.isNotEmpty()) put("fingerprint", JSONArray().put(fp))
                    if (alpn.isNotEmpty()) put("alpn", JSONArray(alpn.split(",")))
                    put("allowInsecure", false)
                })
            } else if (security == "reality") {
                put("security", "reality")
                put("realitySettings", JSONObject().apply {
                    if (sni.isNotEmpty()) put("serverName", sni)
                    if (fp.isNotEmpty()) put("fingerprint", fp)
                    if (pbk.isNotEmpty()) put("publicKey", pbk)
                    if (sid.isNotEmpty()) put("shortId", sid)
                    if (spx.isNotEmpty()) put("spiderX", spx)
                })
            } else {
                put("security", "none")
            }

            when (network) {
                "ws" -> put("wsSettings", JSONObject().apply {
                    if (path.isNotEmpty()) put("path", path)
                    if (host.isNotEmpty()) put("headers", JSONObject().apply {
                        put("Host", host)
                    })
                })
                "grpc" -> put("grpcSettings", JSONObject().apply {
                    if (serviceName.isNotEmpty()) put("serviceName", serviceName)
                })
                "h2", "http" -> put("httpSettings", JSONObject().apply {
                    if (host.isNotEmpty()) put("host", JSONArray().put(host))
                    if (path.isNotEmpty()) put("path", path)
                })
                "quic" -> put("quicSettings", JSONObject())
                "tcp" -> {
                    put("tcpSettings", JSONObject())
                }
            }
        }
    }

    private fun extractFragment(raw: String): Pair<String, String?> {
        val idx = raw.indexOf("#")
        return if (idx >= 0) {
            Pair(raw.substring(0, idx), raw.substring(idx + 1))
        } else {
            Pair(raw, null)
        }
    }

    private fun splitAtSign(serverPart: String): Pair<String?, String> {
        val idx = serverPart.indexOf("@")
        return if (idx >= 0) {
            Pair(serverPart.substring(0, idx), serverPart.substring(idx + 1))
        } else {
            null to serverPart
        }
    }

    private fun parseHostPort(hostPort: String): Pair<String, Int> {
        val cleaned = hostPort.substringBefore("?").substringBefore("/")
        val lastColon = cleaned.lastIndexOf(":")
        return if (lastColon > 0) {
            val host = cleaned.substring(0, lastColon)
            val port = cleaned.substring(lastColon + 1).toIntOrNull() ?: 443
            host to port
        } else {
            cleaned to 443
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").associate {
            val idx = it.indexOf("=")
            if (idx > 0) {
                URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
            } else {
                it to ""
            }
        }
    }

    private fun base64Decode(input: String): String {
        val decoded = Base64.decode(input, Base64.DEFAULT)
        return String(decoded, StandardCharsets.UTF_8)
    }

    fun getServerName(uri: String): String {
        return try {
            val config = generateConfig(uri)
            val json = JSONObject(config)
            val outbound = json.getJSONArray("outbounds").getJSONObject(0)
            val stream = outbound.optJSONObject("streamSettings")
            stream?.optJSONObject("tlsSettings")?.optString("serverName", "")
                ?: stream?.optJSONObject("realitySettings")?.optString("serverName", "")
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
