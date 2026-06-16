package ym.authcode.velocity.storage

import java.util.Locale
import java.util.UUID

data class AuthProfile(
    val id: Long,
    val originalName: String,
    val originalNameLower: String,
    val internalName: String,
    val displayName: String,
    val uuid: UUID,
    val authType: AuthProfileType,
    val premiumBound: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    val internalNameLower: String
        get() = internalName.lowercase(Locale.ROOT)
}

enum class AuthProfileType {
    PREMIUM,
    OFFLINE;

    companion object {
        fun parse(value: String): AuthProfileType {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: OFFLINE
        }
    }
}

data class PendingOfflineRename(
    val id: Long,
    val username: String,
    val usernameLower: String,
    val offlineName: String,
    val ip: String,
    val expiresAt: Long,
    val createdAt: Long
)
