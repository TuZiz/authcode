package ym.authcode.common.model

data class ProxyAuthPayload(
    val version: Int,
    val username: String,
    val uuid: String,
    val premium: Boolean,
    val timestamp: Long,
    val nonce: String,
    val signature: String
) {
    val status: ProxyAuthStatus
        get() = ProxyAuthStatus.fromPremium(premium)
}
