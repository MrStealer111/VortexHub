package com.vpnmobile.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUsageTest {

    @Test
    fun parsesUserInfoHeaderInSeconds() {
        val usage = SubscriptionFetcher.parseUsage(
            "upload=100; download=900; total=10000; expire=1700000000",
        )
        assertNotNull(usage)
        assertEquals(100L, usage!!.upload)
        assertEquals(900L, usage.download)
        assertEquals(1000L, usage.used)
        assertEquals(10000L, usage.total)
        assertEquals(1_700_000_000_000L, usage.expireAt)
        assertTrue(usage.hasQuota)
    }

    @Test
    fun parsesUserInfoHeaderInMilliseconds() {
        val usage = SubscriptionFetcher.parseUsage("expire=1700000000000")
        assertNotNull(usage)
        assertEquals(1_700_000_000_000L, usage!!.expireAt)
    }

    @Test
    fun ignoresHeaderWithoutKnownFields() {
        assertNull(SubscriptionFetcher.parseUsage(null))
        assertNull(SubscriptionFetcher.parseUsage(""))
        assertNull(SubscriptionFetcher.parseUsage("foo=bar"))
    }

    @Test
    fun computesFractionAndLowData() {
        val usage = SubscriptionUsage(upload = 400, download = 500, total = 1000)
        assertEquals(0.9f, usage.fractionUsed(), 0.0001f)
        assertTrue(usage.isLowData())
        assertFalse(SubscriptionUsage(upload = 100, total = 1000).isLowData())
        assertFalse(SubscriptionUsage(total = 0).isLowData())
    }

    @Test
    fun computesDaysLeftAndExpiry() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val soon = SubscriptionUsage(expireAt = now + 2 * day + 1)
        assertEquals(3L, soon.daysLeft(now))
        assertTrue(soon.isExpiringSoon(now))
        assertFalse(soon.isExpired(now))

        val expired = SubscriptionUsage(expireAt = now - 1000)
        assertTrue(expired.isExpired(now))
        assertTrue(expired.daysLeft(now) <= 0L)
        assertFalse(expired.isExpiringSoon(now))
    }

    @Test
    fun subscriptionExposesUsageOnlyWhenPresent() {
        val empty = Subscription(id = "a", name = "A", url = "https://example.com/a")
        assertFalse(empty.hasUsage)
        assertNull(empty.usageOrNull())

        val withUsage = empty.copy(upload = 1, download = 2, total = 10, expireAt = 1_700_000_000_000L)
        assertTrue(withUsage.hasUsage)
        assertEquals(3L, withUsage.usageOrNull()!!.used)
    }
}
