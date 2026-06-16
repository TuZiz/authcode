package ym.authcode.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import ym.authcode.message.MessageService
import java.util.Locale
import java.util.UUID

class GuiItemFactory(
    private val messageService: MessageService
) {
    fun create(
        config: GuiItemConfig,
        variables: Map<String, String>,
        skullOwner: SkullOwner? = null
    ): ItemStack {
        val material = Material.matchMaterial(config.material.uppercase(Locale.ROOT)) ?: Material.STONE
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        if (config.name.isNotBlank()) {
            meta.displayName(messageService.renderRaw(config.name, variables))
        }
        if (config.lore.isNotEmpty()) {
            meta.lore(config.lore.map { messageService.renderRaw(it, variables) })
        }
        config.customModelData?.let { meta.setCustomModelData(it) }
        if (meta is SkullMeta && skullOwner != null) {
            meta.setPlayerProfile(Bukkit.createProfile(skullOwner.uuid, skullOwner.name))
        }
        item.itemMeta = meta
        return item
    }
}

data class SkullOwner(
    val uuid: UUID,
    val name: String
)
