package ym.authcode.model

import java.util.UUID

data class PlayerAuthData(
    val uuid: UUID?,
    val name: String,
    val lowerName: String,
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
)
