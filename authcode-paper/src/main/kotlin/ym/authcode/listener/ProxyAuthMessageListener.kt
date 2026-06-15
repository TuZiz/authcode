package ym.authcode.listener

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import ym.authcode.service.AuthService

class ProxyAuthMessageListener(
    private val authService: AuthService
) : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        authService.handleProxyMessage(player, channel, message)
    }
}
