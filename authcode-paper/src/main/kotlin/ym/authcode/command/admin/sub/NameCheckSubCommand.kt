package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.common.identity.OfflineNameFailure
import ym.authcode.common.identity.OfflineNameResolver
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import ym.authcode.model.PremiumStatus
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PremiumCheckService
import ym.authcode.storage.Storage
import java.util.Locale
import java.util.concurrent.CompletableFuture

class NameCheckSubCommand(
    private val configManager: ConfigManager,
    private val premiumCheckService: PremiumCheckService,
    private val identityDisplayService: IdentityDisplayService,
    private val storage: Storage,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "namecheck"
    override val permission = "authcode.admin.namecheck"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size != 1) {
            messageService.send(sender, "admin.usage-namecheck")
            return
        }
        val input = args[0]
        val settings = configManager.current().offlineName
        val resolved = OfflineNameResolver.resolve(input, settings)
        if (!resolved.success && resolved.failure == OfflineNameFailure.INVALID_NAME) {
            messageService.send(sender, "offline-name.invalid-name")
            return
        }

        val premiumFuture = premiumCheckService.checkName(input)
            .exceptionally { PremiumStatus.FAILED }
        val conflictFuture = if (resolved.success) {
            storage.findPlayerByLowerName(resolved.internalName!!.lowercase(Locale.ROOT))
                .thenApply { it != null }
                .exceptionally { false }
        } else {
            CompletableFuture.completedFuture(false)
        }

        premiumFuture.thenCombine(conflictFuture) { premiumStatus, conflict ->
            val overflow = resolved.failure == OfflineNameFailure.NAME_TOO_LONG ||
                wouldOverflow(input, settings.prefix, settings.enabled, settings.avoidDoublePrefix, settings.maxNameLength)
            val offlineIdentityName = if (resolved.success) {
                identityDisplayService.identityNameRaw(
                    PlayerIdentity(
                        originalName = resolved.originalName!!,
                        internalName = resolved.internalName!!,
                        displayName = resolved.displayName!!,
                        uuid = resolved.uuid!!,
                        premium = false
                    )
                )
            } else {
                ""
            }
            mapOf(
                "input" to input,
                "mojang" to premiumStatusText(premiumStatus),
                "premium_internal_name" to input,
                "offline_internal_name" to (resolved.internalName ?: ""),
                "offline_identity_name" to offlineIdentityName,
                "overflow" to yesNo(overflow, "overflow"),
                "conflict" to yesNo(conflict || resolved.failure == OfflineNameFailure.INTERNAL_NAME_CONFLICT, "conflict")
            )
        }.whenComplete { variables, throwable ->
            if (throwable != null) {
                messageService.send(sender, "general.internal-error")
            } else {
                messageService.send(sender, "admin.namecheck", variables)
            }
        }
    }

    private fun premiumStatusText(status: PremiumStatus): String {
        return when (status) {
            PremiumStatus.PREMIUM -> messageService.plain("admin.mojang-exists")
            PremiumStatus.NOT_PREMIUM -> messageService.plain("admin.mojang-missing")
            PremiumStatus.FAILED -> messageService.plain("admin.mojang-failed")
        }
    }

    private fun yesNo(value: Boolean, key: String): String {
        return messageService.plain("admin.$key-${if (value) "yes" else "no"}")
    }

    private fun wouldOverflow(
        input: String,
        prefix: String,
        enabled: Boolean,
        avoidDoublePrefix: Boolean,
        maxNameLength: Int
    ): Boolean {
        val internalName = if (!enabled || (avoidDoublePrefix && input.startsWith(prefix))) {
            input
        } else {
            prefix + input
        }
        return internalName.length > maxNameLength
    }
}
