package ym.authcode

import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.bootstrap.PluginBootstrap

class AuthCodePlugin : JavaPlugin() {
    private var bootstrap: PluginBootstrap? = null

    override fun onEnable() {
        bootstrap = PluginBootstrap(this).also { it.start() }
    }

    override fun onDisable() {
        bootstrap?.stop()
        bootstrap = null
    }
}
