package ym.authcode.command.admin.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.authcode.command.admin.AdminSubCommand
import ym.authcode.gui.InviteGuiService
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService

class GuiSubCommand(
    private val inviteGuiService: InviteGuiService,
    private val messageService: MessageService
) : AdminSubCommand {
    override val name = "gui"
    override val permission = "authcode.admin.gui"

    override fun execute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: run {
            messageService.send(sender, LangKeys.PLAYER_ONLY)
            return
        }
        inviteGuiService.openMain(player)
    }
}
