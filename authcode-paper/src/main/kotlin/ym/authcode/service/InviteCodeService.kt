package ym.authcode.service

import ym.authcode.model.InviteCode
import ym.authcode.model.InviteUseResult
import ym.authcode.storage.Storage
import ym.authcode.util.RandomCodeGenerator
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

class InviteCodeService(
    private val storage: Storage
) {
    fun createCode(code: String, maxUses: Int, expireTime: Long?, createdBy: String): CompletableFuture<Boolean> {
        val now = System.currentTimeMillis()
        val inviteCode = InviteCode(
            code = code,
            lowerCode = code.lowercase(Locale.ROOT),
            maxUses = maxUses,
            usedCount = 0,
            createdBy = createdBy,
            createdTime = now,
            expireTime = expireTime,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )
        return storage.createInviteCode(inviteCode)
    }

    fun createRandom(maxUses: Int, expireTime: Long?, createdBy: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        attemptRandomCreate(maxUses, expireTime, createdBy, 12, future)
        return future
    }

    fun delete(code: String): CompletableFuture<Boolean> {
        return storage.deleteInviteCode(code.lowercase(Locale.ROOT))
    }

    fun list(): CompletableFuture<List<InviteCode>> {
        return storage.listInviteCodes()
    }

    fun info(code: String): CompletableFuture<InviteCode?> {
        return storage.findInviteCode(code.lowercase(Locale.ROOT))
    }

    fun use(code: String, playerUuid: UUID, playerName: String, ip: String): CompletableFuture<InviteUseResult> {
        return storage.useInviteCode(code, playerUuid, playerName, ip, System.currentTimeMillis())
    }

    private fun attemptRandomCreate(
        maxUses: Int,
        expireTime: Long?,
        createdBy: String,
        remainingAttempts: Int,
        future: CompletableFuture<String>
    ) {
        if (remainingAttempts <= 0) {
            future.completeExceptionally(IllegalStateException("Unable to generate unique invite code"))
            return
        }
        val code = RandomCodeGenerator.generate()
        createCode(code, maxUses, expireTime, createdBy).whenComplete { created, throwable ->
            when {
                throwable != null -> future.completeExceptionally(throwable)
                created == true -> future.complete(code)
                else -> attemptRandomCreate(maxUses, expireTime, createdBy, remainingAttempts - 1, future)
            }
        }
    }
}
