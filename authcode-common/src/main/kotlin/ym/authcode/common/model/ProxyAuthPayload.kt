package ym.authcode.common.model

data class ProxyAuthPayload(
    val version: Int,
    val username: String,
    val uuid: String,
    val originalName: String,
    val internalName: String,
    val displayName: String,
    val premium: Boolean,
    val authType: String,
    val timestamp: Long,
    val nonce: String,
    val signature: String
) {
    val status: ProxyAuthStatus
        get() = ProxyAuthStatus.fromPremium(premium)
}
