package ym.authcode.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ym.authcode.service.AuthService

class PlayerJoinListener(
    private val authService: AuthService
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        authService.handleJoin(event.player)
    }
}
