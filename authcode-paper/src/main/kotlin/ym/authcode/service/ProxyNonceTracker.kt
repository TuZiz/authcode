package ym.authcode.service

import java.util.concurrent.ConcurrentHashMap

class ProxyNonceTracker {
    private val nonces = ConcurrentHashMap<String, Long>()

    fun markIfNew(nonce: String, expireAt: Long, now: Long): Boolean {
        cleanup(now)
        return nonces.putIfAbsent(nonce, expireAt) == null
    }

    fun clear() {
        nonces.clear()
    }

    private fun cleanup(now: Long) {
        nonces.entries.removeIf { it.value <= now }
    }
}
