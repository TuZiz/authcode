package ym.authcode.model

import java.util.UUID

data class InviteCodeUse(
    val code: String,
    val lowerCode: String,
    val playerUuid: UUID,
    val playerName: String,
    val ip: String,
    val useTime: Long
)
