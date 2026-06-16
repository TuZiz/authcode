package ym.authcode.hook

import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.config.ConfigManager
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PlayerIdentityService

object PlaceholderHookRegistrar {
    fun tryRegister(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        identityService: PlayerIdentityService,
        identityDisplayService: IdentityDisplayService
    ): Boolean {
        if (!configManager.current().identityDisplay.applyPlaceholder) {
            return false
        }
        if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") == null) {
            return false
        }
        return runCatching {
            val type = Class.forName("ym.authcode.hook.AuthCodePlaceholderExpansion")
            val expansion = type.getConstructor(
                JavaPlugin::class.java,
                PlayerIdentityService::class.java,
                IdentityDisplayService::class.java
            ).newInstance(plugin, identityService, identityDisplayService)
            val registered = type.getMethod("register").invoke(expansion) as Boolean
            if (registered) {
                plugin.logger.info("Registered AuthCode PlaceholderAPI expansion.")
            }
            registered
        }.getOrElse { throwable ->
            plugin.logger.warning("Failed to register PlaceholderAPI expansion: ${throwable.message}")
            false
        }
    }
}
