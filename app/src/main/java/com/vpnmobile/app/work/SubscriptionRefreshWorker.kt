package com.vpnmobile.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vpnmobile.app.data.SubscriptionRefresher
import com.vpnmobile.app.data.TunnelStore
import com.vpnmobile.app.vpn.VpnNotification
import kotlinx.coroutines.flow.first

class SubscriptionRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = TunnelStore(applicationContext)
        val subs = store.subscriptions.first()
        if (subs.isEmpty()) return Result.success()
        subs.forEach { sub ->
            try {
                val outcome = SubscriptionRefresher.refresh(store, sub)
                if (!outcome.isFirstLoad && outcome.newCount > 0) {
                    VpnNotification.showSubscriptionUpdate(applicationContext, sub.name, outcome.newCount)
                }
            } catch (_: Exception) {
            }
        }
        return Result.success()
    }
}
