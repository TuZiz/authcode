package ym.authcode.util

import org.bukkit.entity.Player

object IpUtil {
    fun playerIp(player: Player): String {
        return player.address?.address?.hostAddress ?: "unknown"
    }
}
