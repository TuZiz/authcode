package ym.authcode.service

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ym.authcode.cache.AuthSessionCache
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import ym.authcode.scheduler.SchedulerAdapter

class LockService(
    private val configManager: ConfigManager,
    private val sessionCache: AuthSessionCache,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) {
    fun lock(player: Player) {
        scheduler.runAtEntity(player) {
            val settings = configManager.current()
            val duration = (settings.auth.authTimeoutSeconds * 20L + 200L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (settings.lock.blindness) {
                player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false, false, false))
            }
            if (settings.lock.slowness) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 4, false, false, false))
            }
        }
    }

    fun unlock(player: Player) {
        scheduler.runAtEntity(player) {
            player.removePotionEffect(PotionEffectType.BLINDNESS)
            player.removePotionEffect(PotionEffectType.SLOWNESS)
        }
    }

    fun scheduleTimeout(player: Player) {
        val uuid = player.uniqueId
        val delay = configManager.current().auth.authTimeoutSeconds.coerceAtLeast(1L) * 20L
        val task = scheduler.runAtEntityDelayed(player, delay) {
            if (!sessionCache.isLocked(uuid)) {
                return@runAtEntityDelayed
            }
            if (configManager.current().auth.kickOnTimeout) {
                messageService.kick(player, "auth.auth-timeout")
            } else {
                messageService.send(player, "auth.auth-timeout")
            }
            sessionCache.remove(uuid)
        }
        sessionCache.setTimeoutTask(uuid, task)
    }

    fun clearPlayer(player: Player) {
        val uuid = player.uniqueId
        sessionCache.remove(uuid)
        unlock(player)
    }
}
