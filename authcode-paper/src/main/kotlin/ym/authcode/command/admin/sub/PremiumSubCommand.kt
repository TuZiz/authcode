package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.message.MessageService
import ym.authcode.storage.Storage
import java.util.Locale

class PremiumSubCommand(
    private val storage: Storage,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "premium"
    override val permission = "authcode.admin.premium"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size != 2) {
            messageService.send(sender, "admin.usage-premium")
            return
        }
        val premium = when (args[1].lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> {
                messageService.send(sender, "admin.usage-premium")
                return
            }
        }
        storage.setPremiumOverride(
            args[0],
            args[0].lowercase(Locale.ROOT),
            premium,
            System.currentTimeMillis()
        ).whenComplete { _, throwable ->
            if (throwable != null) {
                messageService.send(sender, "general.database-error")
            } else {
                messageService.send(
                    sender,
                    "admin.premium-set",
                    mapOf("player" to args[0], "value" to bool(premium))
                )
            }
        }
    }

    private fun bool(value: Boolean): String {
        return messageService.plain(if (value) "admin.boolean-true" else "admin.boolean-false")
    }
}
