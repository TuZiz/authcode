package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import ym.authcode.service.InviteCodeService
import ym.authcode.util.TimeParser

class RandomCodeSubCommand(
    private val inviteCodeService: InviteCodeService,
    private val configManager: ConfigManager,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "random"
    override val permission = "authcode.admin.create"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size !in 0..2) {
            messageService.send(sender, "admin.usage-random")
            return
        }
        val defaults = configManager.current().inviteCode
        val usesInput = args.getOrNull(0)
        val uses = usesInput?.toIntOrNull() ?: defaults.defaultMaxUses
        if ((usesInput != null && usesInput.toIntOrNull() == null) || uses <= 0) {
            messageService.send(sender, "admin.invalid-number")
            return
        }
        val expireTime = try {
            TimeParser.parseExpireTime(args.getOrNull(1) ?: defaults.defaultExpireAfter, System.currentTimeMillis())
        } catch (_: IllegalArgumentException) {
            messageService.send(sender, "admin.invalid-time")
            return
        }
        inviteCodeService.createRandom(uses, expireTime, sender.name).whenComplete { code, throwable ->
            if (throwable != null) {
                messageService.send(sender, "general.database-error")
            } else {
                messageService.send(sender, "admin.code-random-created", mapOf("code" to code))
            }
        }
    }

}
