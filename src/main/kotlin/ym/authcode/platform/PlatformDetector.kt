package ym.authcode.platform

import org.bukkit.Server

class PlatformDetector(
    private val server: Server
) {
    fun isFolia(): Boolean {
        val hasRegionizedServer = runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
        val hasFoliaSchedulers = runCatching {
            server.javaClass.getMethod("getGlobalRegionScheduler")
            server.javaClass.getMethod("getAsyncScheduler")
        }.isSuccess
        return hasRegionizedServer && hasFoliaSchedulers
    }
}
