package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.message.MessageService
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PlayerIdentityService
import ym.authcode.storage.Storage
import java.util.Locale

class IdentitySubCommand(
    private val identityService: PlayerIdentityService,
    private val identityDisplayService: IdentityDisplayService,
    private val storage: Storage,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "identity"
    override val permission = "authcode.admin.identity"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size != 1) {
            messageService.send(sender, "admin.usage-identity")
            return
        }
        val input = args[0]
        val cached = identityService.findByName(input)
        if (cached != null) {
            sendIdentity(sender, cached)
            return
        }
        storage.findPlayerByLowerName(input.lowercase(Locale.ROOT)).whenComplete { data, throwable ->
            if (throwable != null) {
                messageService.send(sender, "general.database-error")
                return@whenComplete
            }
            val uuid = data?.uuid
            if (data == null || uuid == null) {
                messageService.send(sender, "admin.identity-not-found", mapOf("player" to input))
                return@whenComplete
            }
            sendIdentity(
                sender,
                PlayerIdentity(
                    originalName = data.originalName,
                    internalName = data.internalName,
                    displayName = data.displayName,
                    uuid = uuid,
                    premium = data.lastProxyPremium ?: data.premium ?: false
                )
            )
        }
    }

    private fun sendIdentity(sender: CommandSender, identity: PlayerIdentity) {
        val variables = identityDisplayService.variables(identity)
        messageService.send(sender, "identity.debug-header", variables)
        messageService.send(sender, "identity.debug-original-name", variables)
        messageService.send(sender, "identity.debug-internal-name", variables)
        messageService.send(sender, "identity.debug-display-name", variables)
        messageService.send(sender, "identity.debug-premium", variables)
        messageService.send(sender, "identity.debug-uuid", variables)
        messageService.send(sender, "identity.debug-chat-name", variables)
    }
}
