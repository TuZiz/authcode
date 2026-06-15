package ym.authcode.listener

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import ym.authcode.cache.AuthSessionCache
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import java.util.Locale

class AuthLockListener(
    private val configManager: ConfigManager,
    private val sessionCache: AuthSessionCache,
    private val messageService: MessageService
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!configManager.current().lock.blockMove || !isLocked(event.player)) {
            return
        }
        val from = event.from
        val to = event.to ?: return
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            event.to = from
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (configManager.current().lock.blockBreak && cancelIfLocked(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (configManager.current().lock.blockPlace && cancelIfLocked(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (configManager.current().lock.blockInteract && cancelIfLocked(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (configManager.current().lock.blockInteract && cancelIfLocked(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (configManager.current().lock.blockDrop && cancelIfLocked(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (configManager.current().lock.blockPickup && cancelIfLocked(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (configManager.current().lock.blockInventory && cancelIfLocked(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (configManager.current().lock.blockAttack && isLocked(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player
        if (damager != null && configManager.current().lock.blockAttack && cancelIfLocked(damager)) {
            event.isCancelled = true
            return
        }
        val target = event.entity as? Player ?: return
        if (configManager.current().lock.blockAttack && isLocked(target)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (isLocked(event.player)) {
            event.isCancelled = true
            messageService.send(event.player, "auth.action-blocked")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!isLocked(event.player)) {
            return
        }
        val root = event.message.trim()
            .removePrefix("/")
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.lowercase(Locale.ROOT)
            ?: return
        if (root !in configManager.current().commands.allowedBeforeAuth) {
            event.isCancelled = true
            messageService.send(event.player, "auth.command-blocked")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        if (!configManager.current().auth.allowChatBeforeAuth && isLocked(event.player)) {
            event.isCancelled = true
            messageService.send(event.player, "auth.action-blocked")
        }
    }

    private fun cancelIfLocked(player: Player): Boolean {
        if (!isLocked(player)) {
            return false
        }
        messageService.send(player, "auth.action-blocked")
        return true
    }

    private fun isLocked(player: Player): Boolean {
        return sessionCache.isLocked(player.uniqueId)
    }
}
