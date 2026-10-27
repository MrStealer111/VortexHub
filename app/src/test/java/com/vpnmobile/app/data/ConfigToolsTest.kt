package com.vpnmobile.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConfigToolsTest {

    private val ss = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpzZWNyZXQ=@1.2.3.4:443#Demo"

    @Test
    fun parsesShadowsocksEndpoint() {
        assertEquals("1.2.3.4" to 443, ConfigTools.parseEndpoint(ss))
        assertEquals("Shadowsocks", ConfigTools.protocolLabel(ss))
    }

    @Test
    fun parsesWireGuardEndpoint() {
        val config = """
            [Interface]
            PrivateKey = abc
            [Peer]
            PublicKey = def
            Endpoint = vpn.example.com:51820
        """.trimIndent()
        assertEquals("vpn.example.com" to 51820, ConfigTools.parseEndpoint(config))
        assertEquals("WireGuard", ConfigTools.protocolLabel(config))
    }

    @Test
    fun killSwitchReplacesAllowedIps() {
        val config = "[Interface]\n[Peer]\nAllowedIPs = 10.0.0.0/8"
        val result = ConfigTools.applyKillSwitch(config)
        assertTrue(result.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
    }

    @Test
    fun ipv6BlockAddsDefaultRoute() {
        val config = "[Interface]\n[Peer]\nAllowedIPs = 0.0.0.0/0"
        val result = ConfigTools.applyIpv6Block(config)
        assertTrue(result.contains("::/0"))
        assertEquals(1, Regex("::/0").findAll(result).count())
    }

    @Test
    fun normalizesPlainWireGuardConfig() {
        val config = "[Interface]\nPrivateKey = abc\n[Peer]\nPublicKey = def"
        assertEquals(config, ConfigTools.normalizeWireGuard(config))
    }

    @Test
    fun decodesBase64WireGuardLink() {
        val config = "[Interface]\nPrivateKey = abc\n[Peer]\nPublicKey = def\nEndpoint = 1.2.3.4:51820"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(config.toByteArray())
        assertEquals(config, ConfigTools.normalizeWireGuard("wireguard://$encoded"))
    }

    @Test
    fun decodesUrlEncodedWireGuardLink() {
        val config = "[Interface]\nPrivateKey = abc\n[Peer]\nPublicKey = def"
        val encoded = java.net.URLEncoder.encode(config, "UTF-8")
        assertEquals(config, ConfigTools.normalizeWireGuard("wireguard://$encoded"))
    }

    @Test
    fun rejectsNonWireGuardLinks() {
        assertNull(ConfigTools.normalizeWireGuard("ss://abc@1.2.3.4:443"))
        assertNull(ConfigTools.normalizeWireGuard("wireguard://not-a-config"))
    }

    @Test
    fun ipv6BlockIsIdempotent() {
        val config = "[Interface]\n[Peer]\nAllowedIPs = 0.0.0.0/0, ::/0"
        val result = ConfigTools.applyIpv6Block(config)
        assertEquals(1, Regex("::/0").findAll(result).count())
    }
}
