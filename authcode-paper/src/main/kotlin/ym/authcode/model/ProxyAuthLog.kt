package ym.authcode.model

import java.util.UUID

data class ProxyAuthLog(
    val uuid: UUID,
    val name: String,
    val premium: Boolean,
    val authSource: String,
    val remoteIp: String,
    val serverName: String,
    val nonce: String,
    val verifyTime: Long,
    val createdAt: Long
)
