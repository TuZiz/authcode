package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.message.MessageService
import ym.authcode.service.InviteCodeService

class DeleteCodeSubCommand(
    private val inviteCodeService: InviteCodeService,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "delete"
    override val permission = "authcode.admin.delete"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size != 1) {
            messageService.send(sender, "admin.usage-delete")
            return
        }
        inviteCodeService.delete(args[0]).whenComplete { deleted, throwable ->
            when {
                throwable != null -> messageService.send(sender, "general.database-error")
                deleted == true -> messageService.send(sender, "admin.code-deleted", mapOf("code" to args[0]))
                else -> messageService.send(sender, "admin.code-not-found", mapOf("code" to args[0]))
            }
        }
    }
}
