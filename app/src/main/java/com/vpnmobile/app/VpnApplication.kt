package com.vpnmobile.app

import android.app.Application
import com.vpnmobile.app.data.TunnelStore
import com.vpnmobile.app.vpn.VpnConnectionManager

class VpnApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(private val app: Application) {
    val store = TunnelStore(app)
    val vpn = VpnConnectionManager(app)
}
