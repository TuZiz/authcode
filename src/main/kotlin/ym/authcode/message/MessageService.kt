package ym.authcode.message

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.authcode.lang.LangManager
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.util.ComponentUtil

class MessageService(
    private val langManager: LangManager,
    private val scheduler: SchedulerAdapter
) {
    fun send(sender: CommandSender, key: String, variables: Map<String, String> = emptyMap()) {
        val components = render(key, variables)
        if (sender is Player) {
            scheduler.runAtEntity(sender) {
                components.forEach { sender.sendMessage(it) }
            }
        } else {
            scheduler.runGlobal {
                components.forEach { sender.sendMessage(it) }
            }
        }
    }

    fun sendNow(sender: CommandSender, key: String, variables: Map<String, String> = emptyMap()) {
        render(key, variables).forEach { sender.sendMessage(it) }
    }

    fun kick(player: Player, key: String, variables: Map<String, String> = emptyMap()) {
        val component = render(key, variables).first()
        scheduler.runAtEntity(player) {
            player.kick(component)
        }
    }

    fun render(key: String, variables: Map<String, String> = emptyMap()) =
        langManager.getLines(key).map {
            ComponentUtil.render(it, langManager.prefix(), variables)
        }

    fun plain(key: String): String {
        return langManager.getLines(key).firstOrNull() ?: key
    }
}
