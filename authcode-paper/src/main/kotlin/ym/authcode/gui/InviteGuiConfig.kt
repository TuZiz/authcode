package ym.authcode.gui

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.config.ConfigManager
import java.io.File

class InviteGuiConfig(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    fun main(): GuiScreenConfig {
        return load("main.yml")
    }

    fun codeList(): GuiScreenConfig {
        return load("code_list.yml")
    }

    fun playerInfo(): GuiScreenConfig {
        return load("player_info.yml")
    }

    private fun load(fileName: String): GuiScreenConfig {
        val file = File(plugin.dataFolder, "${configManager.current().gui.folder}/$fileName")
        val config = YamlConfiguration.loadConfiguration(file)
        val rows = config.getInt("rows", 6).coerceIn(1, 6)
        val layout = config.getStringList("layout")
            .take(rows)
            .map { it.padEnd(9).take(9) }
        val normalizedLayout = if (layout.isEmpty()) {
            List(rows) { "         " }
        } else {
            layout + List(rows - layout.size) { "         " }
        }
        val items = config.getConfigurationSection("items")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val section = config.getConfigurationSection("items.$key") ?: return@mapNotNull null
                key.firstOrNull()?.let { it to loadItem(section) }
            }
            ?.toMap()
            ?: emptyMap()
        return GuiScreenConfig(
            title = config.getString("title", "") ?: "",
            rows = rows,
            layout = normalizedLayout,
            contentChar = config.getString("content.char")?.firstOrNull(),
            items = items,
            codeItem = config.getConfigurationSection("code-item")?.let { loadItem(it) },
            playerItem = config.getConfigurationSection("player-item")?.let { loadItem(it) }
        )
    }

    private fun loadItem(section: ConfigurationSection): GuiItemConfig {
        return GuiItemConfig(
            material = section.getString("material", "STONE") ?: "STONE",
            customModelData = if (section.isInt("custom-model-data")) section.getInt("custom-model-data") else null,
            name = section.getString("name", "") ?: "",
            lore = section.getStringList("lore"),
            action = section.getString("action"),
            leftClickAction = section.getString("left-click-action"),
            rightClickAction = section.getString("right-click-action")
        )
    }
}

data class GuiScreenConfig(
    val title: String,
    val rows: Int,
    val layout: List<String>,
    val contentChar: Char?,
    val items: Map<Char, GuiItemConfig>,
    val codeItem: GuiItemConfig?,
    val playerItem: GuiItemConfig?
) {
    fun contentSlots(): List<Int> {
        val target = contentChar ?: return emptyList()
        return layout.flatMapIndexed { row, line ->
            line.mapIndexedNotNull { column, char ->
                if (char == target) row * 9 + column else null
            }
        }
    }
}

data class GuiItemConfig(
    val material: String,
    val customModelData: Int?,
    val name: String,
    val lore: List<String>,
    val action: String?,
    val leftClickAction: String?,
    val rightClickAction: String?
)
