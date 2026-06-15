package ym.authcode.command.admin

import org.bukkit.command.CommandSender

interface AdminSubCommand {
    val name: String
    val permission: String

    fun execute(sender: CommandSender, args: List<String>)

    fun tabComplete(sender: CommandSender, args: List<String>): List<String> = emptyList()
}
