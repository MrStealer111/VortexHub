package com.vpnmobile.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {

    private fun ssNode(host: String = "1.2.3.4", port: Int = 443): String {
        val userInfo = Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:secret".toByteArray())
        return "ss://$userInfo@$host:$port#Node%20One"
    }

    @Test
    fun parsesPlainShadowsocksList() {
        val raw = """
            # comment
            ${ssNode()}
            ${ssNode("5.6.7.8", 8443)}
        """.trimIndent()
        val entries = SubscriptionParser.parse(raw, "Test")
        assertEquals(2, entries.size)
        assertEquals("Node One", entries[0].name)
        assertEquals("\uD83D\uDD12", entries[0].flag)
        assertTrue(entries[0].configText.startsWith("ss://"))
    }

    @Test
    fun parsesBase64EncodedList() {
        val plain = "${ssNode()}\n${ssNode("5.6.7.8", 8443)}"
        val encoded = Base64.getEncoder().encodeToString(plain.toByteArray())
        val entries = SubscriptionParser.parse(encoded, "Sub")
        assertEquals(2, entries.size)
    }

    @Test
    fun parsesWireGuardBlocks() {
        val raw = """
            [Interface]
            PrivateKey = abc
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = def
            Endpoint = 1.2.3.4:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val entries = SubscriptionParser.parse(raw, "WG")
        assertEquals(1, entries.size)
        assertTrue(entries[0].configText.contains("[Interface]"))
        assertTrue(entries[0].configText.contains("[Peer]"))
    }

    @Test
    fun ignoresUnsupportedProtocols() {
        val entries = SubscriptionParser.parse("vless://abc@host:443\nnot-a-config", "X")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun acceptsWarp() {
        val entries = SubscriptionParser.parse("warp://auto", "W")
        assertEquals(1, entries.size)
        assertEquals("WARP", ConfigTools.protocolLabel(entries[0].configText))
    }
}
