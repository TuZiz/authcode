package ym.authcode.cache

import ym.authcode.model.PremiumStatus
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PremiumCache {
    private val entries = ConcurrentHashMap<String, PremiumCacheEntry>()

    fun get(name: String, now: Long): PremiumStatus? {
        val entry = entries[name.lowercase(Locale.ROOT)] ?: return null
        if (entry.expireAt <= now) {
            entries.remove(name.lowercase(Locale.ROOT))
            return null
        }
        return entry.status
    }

    fun put(name: String, status: PremiumStatus, ttlMillis: Long, now: Long) {
        entries[name.lowercase(Locale.ROOT)] = PremiumCacheEntry(status, now + ttlMillis)
    }

    fun clear() {
        entries.clear()
    }
}

private data class PremiumCacheEntry(
    val status: PremiumStatus,
    val expireAt: Long
)
