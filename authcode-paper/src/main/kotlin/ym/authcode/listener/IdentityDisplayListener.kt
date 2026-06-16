package ym.authcode.listener

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import ym.authcode.config.ConfigManager
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PlayerIdentityService

class IdentityDisplayListener(
    private val configManager: ConfigManager,
    private val identityService: PlayerIdentityService,
    private val identityDisplayService: IdentityDisplayService
) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val settings = configManager.current().identityDisplay
        if (!settings.enabled || !settings.applyChat || settings.placeholderOnly) {
            return
        }
        val identity = identityService.find(event.player.uniqueId) ?: return
        val identityName = identityDisplayService.identityNameComponent(identity)
        val separator = identityDisplayService.chatSeparatorComponent()
        event.renderer(ChatRenderer.viewerUnaware { _, _, message ->
            identityName.append(separator).append(message)
        })
    }
}
