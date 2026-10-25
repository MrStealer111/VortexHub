package com.vpnmobile.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun redactsShadowsocksCredentials() {
        val output = Diagnostics.redact("connecting ss://Y2hhY2hhMjA6cGFzcw==@1.2.3.4:443")
        assertFalse(output.contains("Y2hhY2hhMjA6cGFzcw=="))
        assertTrue(output.contains("1.2.3.4:443"))
    }

    @Test
    fun redactsWireGuardPrivateKey() {
        val output = Diagnostics.redact("PrivateKey = super-secret-key")
        assertFalse(output.contains("super-secret-key"))
        assertTrue(output.contains("PrivateKey = ***"))
    }

    @Test
    fun redactsJsonPassword() {
        val output = Diagnostics.redact("""{"password":"hunter2","host":"x"}""")
        assertFalse(output.contains("hunter2"))
        assertTrue(output.contains("\"password\":\"***\""))
    }
}
