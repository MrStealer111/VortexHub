package com.vpnmobile.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vpnmobile.app.data.SubscriptionRefresher
import com.vpnmobile.app.data.SubscriptionUsage
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
                val before = sub.usageOrNull()
                val outcome = SubscriptionRefresher.refresh(store, sub)
                if (!outcome.isFirstLoad && outcome.newCount > 0) {
                    VpnNotification.showSubscriptionUpdate(applicationContext, sub.name, outcome.newCount)
                }
                val alert = usageAlert(before, outcome.usage)
                if (alert != null) {
                    VpnNotification.showSubscriptionAlert(applicationContext, alert.first, alert.second)
                }
            } catch (_: Exception) {
            }
        }
        return Result.success()
    }

    private fun usageAlert(before: SubscriptionUsage?, after: SubscriptionUsage?): Pair<String, String>? {
        if (before == null || after == null) return null
        val now = System.currentTimeMillis()
        val wasWarned = before.isExpired(now) || before.isExpiringSoon(now) || before.isLowData()
        if (wasWarned) return null
        return when {
            after.isExpired(now) ->
                "Subscription expired" to "Your subscription has expired. Renew it to keep its servers available."
            after.isExpiringSoon(now) -> {
                val days = after.daysLeft(now).coerceAtLeast(0)
                "Subscription expiring" to "Your subscription expires in $days day(s). Renew it soon."
            }
            after.isLowData() -> {
                val pct = (after.fractionUsed() * 100).toInt().coerceIn(0, 100)
                "Subscription data low" to "You have used $pct% of your subscription data quota."
            }
            else -> null
        }
    }
}
