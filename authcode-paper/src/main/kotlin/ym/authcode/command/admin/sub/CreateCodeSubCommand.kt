package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import ym.authcode.service.InviteCodeService
import ym.authcode.util.TimeParser

class CreateCodeSubCommand(
    private val inviteCodeService: InviteCodeService,
    private val configManager: ConfigManager,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "create"
    override val permission = "authcode.admin.create"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            inviteCodeService.createDefault(sender.name).whenComplete { code, throwable ->
                when {
                    throwable != null -> messageService.send(sender, "general.database-error")
                    else -> messageService.send(sender, "admin.code-random-created", mapOf("code" to code))
                }
            }
            return
        }
        if (args.size !in 1..3) {
            messageService.send(sender, "admin.usage-create")
            return
        }
        val defaults = configManager.current().inviteCode
        val usesInput = args.getOrNull(1)
        val uses = usesInput?.toIntOrNull() ?: defaults.defaultMaxUses
        if ((usesInput != null && usesInput.toIntOrNull() == null) || uses <= 0) {
            messageService.send(sender, "admin.invalid-number")
            return
        }
        val expireTime = try {
            TimeParser.parseExpireTime(args.getOrNull(2) ?: defaults.defaultExpireAfter, System.currentTimeMillis())
        } catch (_: IllegalArgumentException) {
            messageService.send(sender, "admin.invalid-time")
            return
        }
        inviteCodeService.createCode(args[0], uses, expireTime, sender.name).whenComplete { created, throwable ->
            when {
                throwable != null -> messageService.send(sender, "general.database-error")
                created == true -> messageService.send(sender, "admin.code-created", mapOf("code" to args[0]))
                else -> messageService.send(sender, "admin.code-already-exists", mapOf("code" to args[0]))
            }
        }
    }

}
