package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.message.MessageService
import ym.authcode.service.InviteCodeService
import ym.authcode.util.TimeParser

class ListCodeSubCommand(
    private val inviteCodeService: InviteCodeService,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "list"
    override val permission = "authcode.admin.list"

    override fun execute(sender: CommandSender, args: List<String>) {
        inviteCodeService.list().whenComplete { codes, throwable ->
            if (throwable != null) {
                messageService.send(sender, "general.database-error")
                return@whenComplete
            }
            if (codes.isNullOrEmpty()) {
                messageService.send(sender, "admin.list-empty")
                return@whenComplete
            }
            val now = System.currentTimeMillis()
            messageService.send(sender, "admin.list-header")
            codes.forEach { code ->
                messageService.send(
                    sender,
                    "admin.list-line",
                    mapOf(
                        "code" to code.code,
                        "max" to code.maxUses.toString(),
                        "remaining" to code.remainingUses().toString(),
                        "used" to code.usedCount.toString(),
                        "expired" to bool(code.isExpired(now)),
                        "enabled" to bool(code.enabled),
                        "creator" to code.createdBy,
                        "created" to TimeParser.format(code.createdTime, never()),
                        "expire" to TimeParser.format(code.expireTime, never())
                    )
                )
            }
        }
    }

    private fun bool(value: Boolean): String {
        return messageService.plain(if (value) "admin.boolean-true" else "admin.boolean-false")
    }

    private fun never(): String {
        return messageService.plain("admin.time-never")
    }
}
