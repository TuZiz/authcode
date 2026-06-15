package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.message.MessageService
import ym.authcode.service.InviteCodeService
import ym.authcode.util.TimeParser

class CreateCodeSubCommand(
    private val inviteCodeService: InviteCodeService,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "create"
    override val permission = "authcode.admin.create"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size !in 2..3) {
            messageService.send(sender, "admin.usage-create")
            return
        }
        val uses = args[1].toIntOrNull()
        if (uses == null || uses <= 0) {
            messageService.send(sender, "admin.invalid-number")
            return
        }
        val expireTime = try {
            TimeParser.parseExpireTime(args.getOrNull(2), System.currentTimeMillis())
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
