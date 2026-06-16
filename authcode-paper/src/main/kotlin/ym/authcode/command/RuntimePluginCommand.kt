package ym.authcode.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class RuntimePluginCommand(
    name: String,
    aliases: List<String>,
    private val executor: CommandExecutor,
    private val tabCompleter: TabCompleter?
) : Command(name) {
    init {
        setAliases(aliases)
        setUsage("/$name")
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
        return executor.onCommand(sender, this, commandLabel, args)
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
        return tabCompleter?.onTabComplete(sender, this, alias, args) ?: emptyList()
    }
}
