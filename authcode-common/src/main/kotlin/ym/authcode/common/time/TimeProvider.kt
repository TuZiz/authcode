package ym.authcode.common.time

interface TimeProvider {
    fun nowMillis(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long {
        return System.currentTimeMillis()
    }
}
