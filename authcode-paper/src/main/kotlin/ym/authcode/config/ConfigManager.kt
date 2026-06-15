package ym.authcode.config

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ConfigManager(
    private val plugin: JavaPlugin
) {
    @Volatile
    private var settings: PluginSettings? = null

    fun load() {
        ensureDefaultFiles()
        plugin.reloadConfig()
        settings = PluginSettings.from(plugin.config)
        ensureGuiFolder()
    }

    fun reload() {
        plugin.reloadConfig()
        settings = PluginSettings.from(plugin.config)
        ensureGuiFolder()
    }

    fun current(): PluginSettings {
        return settings ?: error("Config has not been loaded")
    }

    fun ensureDefaultFiles() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        plugin.saveDefaultConfig()
        saveResourceIfMissing("lang/zh_cn.yml")
        File(plugin.dataFolder, "gui").mkdirs()
    }

    fun ensureGuiFolder() {
        File(plugin.dataFolder, currentOrDefaultGuiFolder()).mkdirs()
    }

    private fun currentOrDefaultGuiFolder(): String {
        return settings?.gui?.folder ?: "gui"
    }

    private fun saveResourceIfMissing(path: String) {
        val file = File(plugin.dataFolder, path)
        if (!file.exists()) {
            plugin.saveResource(path, false)
        }
    }
}
