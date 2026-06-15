package ym.authcode.command.admin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import java.util.Locale

class AuthCodeCommand(
    private val messageService: MessageService,
    subCommands: List<AdminSubCommand>
) : CommandExecutor, TabCompleter {
    private val subCommandMap = subCommands.associateBy { it.name.lowercase(Locale.ROOT) }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            messageService.send(sender, "admin.help")
            return true
        }
        val subCommand = subCommandMap[args[0].lowercase(Locale.ROOT)] ?: run {
            messageService.send(sender, LangKeys.UNKNOWN_COMMAND)
            return true
        }
        if (!sender.hasPermission("authcode.admin") && !sender.hasPermission(subCommand.permission)) {
            messageService.send(sender, LangKeys.NO_PERMISSION)
            return true
        }
        subCommand.execute(sender, args.drop(1))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val prefix = args[0].lowercase(Locale.ROOT)
            return subCommandMap.values
                .filter { sender.hasPermission("authcode.admin") || sender.hasPermission(it.permission) }
                .map { it.name }
                .filter { it.startsWith(prefix, ignoreCase = true) }
        }
        val subCommand = subCommandMap[args.firstOrNull()?.lowercase(Locale.ROOT)] ?: return emptyList()
        return subCommand.tabComplete(sender, args.drop(1))
    }
}
