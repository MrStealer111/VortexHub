package com.vpnmobile.app.data

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigTest {

    private val uuid = "11111111-2222-3333-4444-555555555555"

    private fun outbound(uri: String): JSONObject =
        JSONObject(XrayConfig.build(uri)).getJSONArray("outbounds").getJSONObject(0)

    private fun vmess(vararg extra: Pair<String, Any?>): String {
        val obj = JSONObject()
            .put("v", "2")
            .put("ps", "VMess Node")
            .put("add", "vm.example.com")
            .put("port", "443")
            .put("id", uuid)
            .put("aid", "0")
            .put("scy", "auto")
            .put("net", "ws")
            .put("type", "none")
            .put("host", "cdn.example.com")
            .put("path", "/vm")
            .put("tls", "tls")
            .put("sni", "cdn.example.com")
        extra.forEach { (key, value) -> obj.put(key, value) }
        return "vmess://" + Base64.getEncoder().encodeToString(obj.toString().toByteArray())
    }

    @Test
    fun buildsVlessRealityTcpConfig() {
        val uri = "vless://$uuid@203.0.113.10:443?encryption=none&security=reality" +
            "&sni=www.example.com&fp=chrome&pbk=public-key&sid=ab12&spx=%2F" +
            "&flow=xtls-rprx-vision&type=tcp#Reality%20Node"

        val ob = outbound(uri)
        assertEquals("vless", ob.getString("protocol"))
        val vnext = ob.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("203.0.113.10", vnext.getString("address"))
        assertEquals(443, vnext.getInt("port"))
        val user = vnext.getJSONArray("users").getJSONObject(0)
        assertEquals(uuid, user.getString("id"))
        assertEquals("none", user.getString("encryption"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))

        val stream = ob.getJSONObject("streamSettings")
        assertEquals("tcp", stream.getString("network"))
        assertEquals("reality", stream.getString("security"))
        val reality = stream.getJSONObject("realitySettings")
        assertEquals("www.example.com", reality.getString("serverName"))
        assertEquals("public-key", reality.getString("publicKey"))
        assertEquals("ab12", reality.getString("shortId"))
        assertEquals("/", reality.getString("spiderX"))
        assertEquals("Reality Node", XrayConfig.displayName(uri))
    }

    @Test
    fun buildsVlessWebSocketTlsConfig() {
        val uri = "vless://$uuid@example.com:8443?security=tls&sni=example.com" +
            "&type=ws&host=cdn.example.com&path=%2Fws%3Fed%3D2048&alpn=h2%2Chttp%2F1.1#WS"

        val stream = outbound(uri).getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))
        assertEquals("/ws?ed=2048", stream.getJSONObject("wsSettings").getString("path"))
        assertEquals(
            "cdn.example.com",
            stream.getJSONObject("wsSettings").getJSONObject("headers").getString("Host"),
        )
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("example.com", tls.getString("serverName"))
        assertEquals("h2", tls.getJSONArray("alpn").getString(0))
    }

    @Test
    fun buildsVmessWebSocketConfig() {
        val uri = vmess()
        val ob = outbound(uri)
        assertEquals("vmess", ob.getString("protocol"))
        val vnext = ob.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("vm.example.com", vnext.getString("address"))
        assertEquals(443, vnext.getInt("port"))
        val user = vnext.getJSONArray("users").getJSONObject(0)
        assertEquals(uuid, user.getString("id"))
        assertEquals(0, user.getInt("alterId"))
        assertEquals("auto", user.getString("security"))

        val stream = ob.getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("/vm", stream.getJSONObject("wsSettings").getString("path"))
        assertEquals("tls", stream.getString("security"))
        assertEquals("VMess Node", XrayConfig.displayName(uri))
    }

    @Test
    fun buildsTrojanGrpcConfig() {
        val uri = "trojan://p%40ssw0rd@trojan.example.com:443?security=tls" +
            "&sni=trojan.example.com&type=grpc&serviceName=grpcsvc#Trojan"

        val ob = outbound(uri)
        assertEquals("trojan", ob.getString("protocol"))
        val server = ob.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("trojan.example.com", server.getString("address"))
        assertEquals("p@ssw0rd", server.getString("password"))
        val stream = ob.getJSONObject("streamSettings")
        assertEquals("grpc", stream.getString("network"))
        assertEquals("grpcsvc", stream.getJSONObject("grpcSettings").getString("serviceName"))
        assertEquals("tls", stream.getString("security"))
    }

    @Test
    fun parsesEndpointsForAllProtocols() {
        val vless = "vless://$uuid@1.2.3.4:443?security=tls#V"
        assertEquals("1.2.3.4" to 443, XrayConfig.parseEndpoint(vless))
        assertEquals("1.2.3.4" to 443, ConfigTools.parseEndpoint(vless))
        assertEquals("vm.example.com" to 443, XrayConfig.parseEndpoint(vmess()))
        assertEquals("vm.example.com" to 443, ConfigTools.parseEndpoint(vmess()))
        val trojan = "trojan://pw@[2001:db8::1]:8443?security=tls#T"
        assertEquals("2001:db8::1" to 8443, XrayConfig.parseEndpoint(trojan))
    }

    @Test
    fun rejectsRealityWithoutPublicKey() {
        val uri = "vless://$uuid@example.com:443?security=reality&sni=example.com"
        val error = runCatching { XrayConfig.build(uri) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("pbk", ignoreCase = true))
    }

    @Test
    fun rejectsInvalidUuidAndTransport() {
        assertNotNull(runCatching { XrayConfig.build("vless://not-a-uuid@example.com:443") }.exceptionOrNull())
        val badTransport = "vless://$uuid@example.com:443?type=madeup"
        assertNotNull(runCatching { XrayConfig.build(badTransport) }.exceptionOrNull())
    }

    @Test
    fun doesNotTreatWarpLinkAsXray() {
        assertTrue(!XrayConfig.isXrayUri("vless://warp@162.159.192.1:2408"))
        assertTrue(XrayConfig.isXrayUri("vless://$uuid@example.com:443"))
        assertNull(XrayConfig.protocolName("ss://abc@1.2.3.4:443"))
    }

    @Test
    fun omitsFlowOnPlaintextVless() {
        val uri = "vless://$uuid@example.com:10086?security=none&flow=xtls-rprx-vision"
        val ob = outbound(uri)
        val user = ob.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertTrue(!user.has("flow"))
        assertEquals("none", ob.getJSONObject("streamSettings").getString("security"))
    }

    @Test
    fun buildsPlaintextTrojanAndXhttp() {
        val trojan = "trojan://testpass@example.com:10088?security=none"
        assertEquals("none", outbound(trojan).getJSONObject("streamSettings").getString("security"))

        val xhttp = "vless://$uuid@example.com:443?security=tls&type=xhttp" +
            "&path=%2Fx&host=cdn.example.com&mode=stream"
        val stream = outbound(xhttp).getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("network"))
        val settings = stream.getJSONObject("xhttpSettings")
        assertEquals("/x", settings.getString("path"))
        assertEquals("stream", settings.getString("mode"))
    }

    @Test
    fun includesLocalSocksInboundForGeoLookup() {
        val inbounds = JSONObject(XrayConfig.build("vless://$uuid@example.com:443")).getJSONArray("inbounds")
        assertEquals(2, inbounds.length())
        val socks = (0 until inbounds.length()).map { inbounds.getJSONObject(it) }
            .first { it.getString("tag") == "socks-in" }
        assertEquals("socks", socks.getString("protocol"))
        assertEquals("127.0.0.1", socks.getString("listen"))
        assertEquals(10808, socks.getInt("port"))
    }

    @Test
    fun usesLocalDnsForwarder() {
        val cfg = JSONObject(XrayConfig.build("vless://$uuid@example.com:443"))
        assertEquals("dns-out", cfg.getJSONObject("dns").getString("tag"))
        val rule = cfg.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("dns-out", rule.getString("outboundTag"))
        assertEquals("udp", rule.getString("network"))
        assertEquals("53", rule.getString("port"))
    }

    @Test
    fun keepsLiteralPlusInRealityKey() {
        val uri = "vless://$uuid@example.com:443?security=reality&pbk=ab+cd%2Fef&sni=example.com"
        val reality = outbound(uri).getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals("ab+cd/ef", reality.getString("publicKey"))
    }

    @Test
    fun subscriptionParserAcceptsXrayLinks() {
        val payload = listOf(
            "vless://$uuid@1.2.3.4:443?security=tls#VlessSub",
            vmess(),
            "trojan://pw@5.6.7.8:443?security=tls#TrojanSub",
        ).joinToString("\n")
        val entries = SubscriptionParser.parse(payload)
        assertEquals(3, entries.size)
        assertEquals("VlessSub", entries[0].name)
        assertEquals("VMess Node", entries[1].name)
        assertEquals("TrojanSub", entries[2].name)
    }
}
