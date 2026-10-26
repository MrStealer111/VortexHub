package com.vpnmobile.app.data

import kotlinx.coroutines.flow.first

object SubscriptionRefresher {

    data class Outcome(
        val total: Int,
        val newCount: Int,
        val isFirstLoad: Boolean,
        val usage: SubscriptionUsage? = null,
    )

    suspend fun refresh(store: TunnelStore, sub: Subscription): Outcome {
        val before = store.profiles.first().filter { it.subscriptionId == sub.id }
        val beforeKeys = before.map { it.name to it.configText }.toSet()
        val fetched = SubscriptionFetcher.fetchResult(sub.url)
        fetched.usage?.let { store.updateSubscriptionUsage(sub.id, it) }
        val entries = SubscriptionParser.parse(fetched.body, sub.name)
        if (entries.isEmpty()) {
            return Outcome(total = 0, newCount = 0, isFirstLoad = before.isEmpty(), usage = fetched.usage)
        }
        store.replaceSubscriptionProfiles(sub.id, entries)
        val newCount = entries.count { (it.name to it.configText) !in beforeKeys }
        return Outcome(
            total = entries.size,
            newCount = newCount,
            isFirstLoad = before.isEmpty(),
            usage = fetched.usage,
        )
    }
}
