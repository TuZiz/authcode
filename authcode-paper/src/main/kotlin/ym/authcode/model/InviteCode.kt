package ym.authcode.model

data class InviteCode(
    val code: String,
    val lowerCode: String,
    val maxUses: Int,
    val usedCount: Int,
    val createdBy: String,
    val createdTime: Long,
    val expireTime: Long?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun remainingUses(): Int {
        return (maxUses - usedCount).coerceAtLeast(0)
    }

    fun isExpired(now: Long): Boolean {
        return expireTime != null && now >= expireTime
    }
}
