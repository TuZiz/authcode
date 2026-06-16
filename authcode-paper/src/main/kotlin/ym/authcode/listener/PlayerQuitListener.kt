package ym.authcode.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import ym.authcode.service.LockService
import ym.authcode.service.PlayerIdentityService

class PlayerQuitListener(
    private val lockService: LockService,
    private val identityService: PlayerIdentityService
) : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lockService.clearPlayer(event.player)
        identityService.remove(event.player.uniqueId)
    }
}
