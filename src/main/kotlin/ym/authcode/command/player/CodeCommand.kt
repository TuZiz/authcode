package ym.authcode.command.player

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import ym.authcode.service.AuthService

class CodeCommand(
    private val authService: AuthService,
    private val messageService: MessageService
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            messageService.send(sender, LangKeys.PLAYER_ONLY)
            return true
        }
        if (args.size != 1) {
            messageService.send(player, "command.code-usage")
            return true
        }
        authService.submitCode(player, args[0])
        return true
    }
}
