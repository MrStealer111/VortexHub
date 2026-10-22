package com.vpnmobile.app

import android.net.VpnService
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpnmobile.app.data.ShadowsocksUri
import com.vpnmobile.app.vpn.ShadowsocksRuntime
import com.vpnmobile.app.vpn.ShadowsocksVpnService
import com.vpnmobile.app.vpn.VpnState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShadowsocksTunnelTest {

    @Test
    fun outlineTunnelConnects() {
        runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val uri = InstrumentationRegistry.getArguments().getString("ssUri")
        assertNotNull("Pass -e ssUri <ss://...>", uri)
        val config = ShadowsocksUri.parse(uri!!)
        assertNotNull("Could not parse Shadowsocks URI", config)
        val prepareIntent = VpnService.prepare(context)
        assertNull("VPN consent required: ${prepareIntent?.component}", prepareIntent)

        ActivityScenario.launch(MainActivity::class.java).use {
            ShadowsocksVpnService.start(context, "e2e", config!!, false, "exclude", emptySet())
            val state = withTimeout(40_000) {
                ShadowsocksRuntime.state.first {
                    it == VpnState.CONNECTED ||
                        (it == VpnState.DISCONNECTED && ShadowsocksRuntime.lastError != null)
                }
            }
            assertEquals(ShadowsocksRuntime.lastError ?: "not connected", VpnState.CONNECTED, state)
            val holdSeconds = InstrumentationRegistry.getArguments().getString("holdSeconds")?.toIntOrNull() ?: 5
            Thread.sleep(holdSeconds * 1_000L)
            ShadowsocksVpnService.stop(context)
            ShadowsocksRuntime.state.first { it == VpnState.DISCONNECTED }
        }
        }
    }
}
