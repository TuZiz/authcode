package ym.authcode.service

import org.bukkit.entity.Player
import ym.authcode.util.IpUtil
import java.util.Locale
import java.util.UUID

data class PlayerSnapshot(
    val uuid: UUID,
    val name: String,
    val lowerName: String,
    val ip: String
) {
    companion object {
        fun from(player: Player): PlayerSnapshot {
            return PlayerSnapshot(
                uuid = player.uniqueId,
                name = player.name,
                lowerName = player.name.lowercase(Locale.ROOT),
                ip = IpUtil.playerIp(player)
            )
        }
    }
}
