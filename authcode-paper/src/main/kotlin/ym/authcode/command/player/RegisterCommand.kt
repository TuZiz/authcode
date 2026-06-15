package ym.authcode.command.player

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import ym.authcode.service.AuthService

class RegisterCommand(
    private val authService: AuthService,
    private val messageService: MessageService
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            messageService.send(sender, LangKeys.PLAYER_ONLY)
            return true
        }
        if (args.size != 2) {
            messageService.send(player, "command.reg-usage")
            return true
        }
        authService.register(player, args[0], args[1])
        return true
    }
}
