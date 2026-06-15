package ym.authcode.common.model

enum class ProxyAuthStatus {
    PREMIUM,
    OFFLINE;

    companion object {
        fun fromPremium(premium: Boolean): ProxyAuthStatus {
            return if (premium) PREMIUM else OFFLINE
        }
    }
}
