package ym.authcode.command.player

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import ym.authcode.service.AuthService

class ChangePasswordCommand(
    private val authService: AuthService,
    private val messageService: MessageService
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            messageService.send(sender, LangKeys.PLAYER_ONLY)
            return true
        }
        if (args.size != 3) {
            messageService.send(player, "command.changepass-usage")
            return true
        }
        authService.changePassword(player, args[0], args[1], args[2])
        return true
    }
}
