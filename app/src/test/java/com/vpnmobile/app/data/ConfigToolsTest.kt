package com.vpnmobile.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun ipv6BlockIsIdempotent() {
        val config = "[Interface]\n[Peer]\nAllowedIPs = 0.0.0.0/0, ::/0"
        val result = ConfigTools.applyIpv6Block(config)
        assertEquals(1, Regex("::/0").findAll(result).count())
    }
}
