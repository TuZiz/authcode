package ym.authcode.lang

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.config.ConfigManager
import java.io.File

class LangManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    @Volatile
    private var lang: YamlConfiguration = YamlConfiguration()

    fun load() {
        lang = YamlConfiguration.loadConfiguration(langFile())
    }

    fun reload() {
        lang = YamlConfiguration.loadConfiguration(langFile())
    }

    fun getLines(key: String): List<String> {
        val config = lang
        return when {
            config.isList(key) -> config.getStringList(key)
            config.isString(key) -> listOf(config.getString(key) ?: missing(key))
            else -> listOf(missing(key))
        }
    }

    fun prefix(): String {
        return lang.getString("prefix") ?: ""
    }

    private fun langFile(): File {
        val path = configManager.current().language.file
        val file = File(plugin.dataFolder, path)
        if (!file.exists()) {
            plugin.saveResource(path, false)
        }
        return file
    }

    private fun missing(key: String): String {
        return "<red>Missing language key: $key</red>"
    }
}
