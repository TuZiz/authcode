package ym.authcode.storage

import ym.authcode.model.InviteCode
import ym.authcode.model.InviteUseResult
import ym.authcode.model.PlayerAuthData
import ym.authcode.model.ProxyAuthLog
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface Storage {
    fun initialize(): CompletableFuture<Void>

    fun findPlayerByLowerName(lowerName: String): CompletableFuture<PlayerAuthData?>

    fun saveRegisteredPlayer(
        uuid: UUID,
        name: String,
        lowerName: String,
        passwordHash: String,
        invitedByCode: String,
        ip: String,
        now: Long
    ): CompletableFuture<Void>

    fun updateLogin(lowerName: String, ip: String, now: Long): CompletableFuture<Void>

    fun updatePassword(lowerName: String, passwordHash: String, now: Long): CompletableFuture<Void>

    fun setPremiumOverride(name: String, lowerName: String, premium: Boolean, now: Long): CompletableFuture<Void>

    fun updateProxyAuthStatus(
        uuid: UUID,
        name: String,
        lowerName: String,
        premium: Boolean,
        authSource: String,
        now: Long
    ): CompletableFuture<Void>

    fun recordProxyAuthLog(log: ProxyAuthLog): CompletableFuture<Void>

    fun createInviteCode(code: InviteCode): CompletableFuture<Boolean>

    fun deleteInviteCode(lowerCode: String): CompletableFuture<Boolean>

    fun findInviteCode(lowerCode: String): CompletableFuture<InviteCode?>

    fun listInviteCodes(): CompletableFuture<List<InviteCode>>

    fun useInviteCode(
        code: String,
        playerUuid: UUID,
        playerName: String,
        ip: String,
        now: Long
    ): CompletableFuture<InviteUseResult>

    fun close()
}
