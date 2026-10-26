package com.vpnmobile.app

import android.app.Application
import com.vpnmobile.app.data.TunnelStore
import com.vpnmobile.app.vpn.VpnConnectionManager
import com.vpnmobile.app.work.SubscriptionRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VpnApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val interval = container.store.autoRefreshInterval.first()
                SubscriptionRefreshScheduler.sync(this@VpnApplication, interval)
            }
        }
    }
}

class AppContainer(private val app: Application) {
    val store = TunnelStore(app)
    val vpn = VpnConnectionManager(app)
}
