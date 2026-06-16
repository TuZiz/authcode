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
        ensureGuiFiles()
    }

    fun reload() {
        plugin.reloadConfig()
        settings = PluginSettings.from(plugin.config)
        ensureGuiFiles()
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
        ensureGuiFiles("gui")
    }

    fun ensureGuiFiles(folder: String = currentOrDefaultGuiFolder()) {
        File(plugin.dataFolder, folder).mkdirs()
        saveResourceIfMissing("gui/main.yml", "$folder/main.yml")
        saveResourceIfMissing("gui/code_list.yml", "$folder/code_list.yml")
        saveResourceIfMissing("gui/player_info.yml", "$folder/player_info.yml")
    }

    private fun currentOrDefaultGuiFolder(): String {
        return settings?.gui?.folder ?: "gui"
    }

    private fun saveResourceIfMissing(resourcePath: String, targetPath: String = resourcePath) {
        val file = File(plugin.dataFolder, targetPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            if (resourcePath == targetPath) {
                plugin.saveResource(resourcePath, false)
                return
            }
            plugin.getResource(resourcePath)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: plugin.logger.warning("Missing bundled resource: $resourcePath")
        }
    }
}
