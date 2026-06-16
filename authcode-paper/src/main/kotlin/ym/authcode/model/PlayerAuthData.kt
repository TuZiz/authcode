package ym.authcode.model

import java.util.UUID

data class PlayerAuthData(
    val uuid: UUID?,
    val originalName: String,
    val internalName: String,
    val lowerInternalName: String,
    val displayName: String,
    val passwordHash: String?,
    val premium: Boolean?,
    val registered: Boolean,
    val invitedByCode: String?,
    val registerIp: String?,
    val lastIp: String?,
    val authSource: String?,
    val lastProxyPremium: Boolean?,
    val lastProxyVerifyTime: Long,
    val registerTime: Long,
    val lastLoginTime: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    val name: String
        get() = internalName

    val lowerName: String
        get() = lowerInternalName
}
