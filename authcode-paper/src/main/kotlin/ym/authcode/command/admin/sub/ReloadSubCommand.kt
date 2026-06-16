package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.config.ConfigManager
import ym.authcode.lang.LangManager
import ym.authcode.message.MessageService
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PlayerIdentityService

class ReloadSubCommand(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val langManager: LangManager,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter,
    private val identityService: PlayerIdentityService,
    private val identityDisplayService: IdentityDisplayService
) : AdminSubCommand {
    override val name = "reload"
    override val permission = "authcode.admin.reload"

    override fun execute(sender: CommandSender, args: List<String>) {
        messageService.send(sender, "general.reload-start")
        scheduler.runAsync {
            try {
                configManager.reload()
                langManager.reload()
                reapplyIdentityDisplays()
                messageService.send(sender, "general.reload-success")
            } catch (throwable: Throwable) {
                messageService.send(sender, "general.internal-error")
            }
        }
    }

    private fun reapplyIdentityDisplays() {
        scheduler.runGlobal {
            plugin.server.onlinePlayers.forEach { player ->
                val identity = identityService.find(player.uniqueId) ?: return@forEach
                identityDisplayService.apply(player, identity)
            }
        }
    }
}
