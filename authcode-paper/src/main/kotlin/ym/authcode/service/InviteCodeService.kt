package ym.authcode.service

import ym.authcode.config.ConfigManager
import ym.authcode.model.InviteCode
import ym.authcode.model.InviteCodeUse
import ym.authcode.model.InviteUseResult
import ym.authcode.storage.Storage
import ym.authcode.util.RandomCodeGenerator
import ym.authcode.util.TimeParser
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

class InviteCodeService(
    private val storage: Storage,
    private val configManager: ConfigManager
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

    fun createDefault(createdBy: String): CompletableFuture<String> {
        return try {
            val defaults = configManager.current().inviteCode
            val expireTime = TimeParser.parseExpireTime(defaults.defaultExpireAfter, System.currentTimeMillis())
            createRandom(defaults.defaultMaxUses, expireTime, createdBy)
        } catch (throwable: Throwable) {
            CompletableFuture.failedFuture(throwable)
        }
    }

    fun delete(code: String): CompletableFuture<Boolean> {
        return storage.deleteInviteCode(code.lowercase(Locale.ROOT))
    }

    fun list(): CompletableFuture<List<InviteCode>> {
        return storage.listInviteCodes()
    }

    fun listUses(code: String): CompletableFuture<List<InviteCodeUse>> {
        return storage.listInviteCodeUses(code.lowercase(Locale.ROOT))
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
        val settings = configManager.current().inviteCode
        val length = RandomCodeGenerator.nextLength(settings.randomMinLength, settings.randomMaxLength)
        val code = if (settings.randomDigitsOnly) {
            RandomCodeGenerator.generateDigits(length)
        } else {
            RandomCodeGenerator.generate(length)
        }
        createCode(code, maxUses, expireTime, createdBy).whenComplete { created, throwable ->
            when {
                throwable != null -> future.completeExceptionally(throwable)
                created == true -> future.complete(code)
                else -> attemptRandomCreate(maxUses, expireTime, createdBy, remainingAttempts - 1, future)
            }
        }
    }
}
