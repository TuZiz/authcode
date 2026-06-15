package ym.authcode.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import ym.authcode.service.LockService

class PlayerQuitListener(
    private val lockService: LockService
) : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lockService.clearPlayer(event.player)
    }
}
