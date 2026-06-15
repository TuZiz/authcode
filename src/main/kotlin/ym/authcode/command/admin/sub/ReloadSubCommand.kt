package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.config.ConfigManager
import ym.authcode.lang.LangManager
import ym.authcode.message.MessageService
import ym.authcode.scheduler.SchedulerAdapter

class ReloadSubCommand(
    private val configManager: ConfigManager,
    private val langManager: LangManager,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) : AdminSubCommand {
    override val name = "reload"
    override val permission = "authcode.admin.reload"

    override fun execute(sender: CommandSender, args: List<String>) {
        messageService.send(sender, "general.reload-start")
        scheduler.runAsync {
            try {
                configManager.reload()
                langManager.reload()
                messageService.send(sender, "general.reload-success")
            } catch (throwable: Throwable) {
                messageService.send(sender, "general.internal-error")
            }
        }
    }
}
