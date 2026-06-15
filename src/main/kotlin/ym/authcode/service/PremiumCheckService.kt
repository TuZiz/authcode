package ym.authcode.service

import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.cache.PremiumCache
import ym.authcode.config.ConfigManager
import ym.authcode.model.PremiumStatus
import ym.authcode.scheduler.SchedulerAdapter
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

class PremiumCheckService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val premiumCache: PremiumCache,
    private val scheduler: SchedulerAdapter
) {
    fun checkName(name: String): CompletableFuture<PremiumStatus> {
        val now = System.currentTimeMillis()
        premiumCache.get(name, now)?.let { return CompletableFuture.completedFuture(it) }
        val future = CompletableFuture<PremiumStatus>()
        scheduler.runAsync {
            val status = runCatching { queryMojang(name) }.getOrElse {
                plugin.logger.warning("Mojang API query failed for $name: ${it.message}")
                PremiumStatus.FAILED
            }
            if (status != PremiumStatus.FAILED) {
                val ttlMillis = configManager.current().premium.cacheMinutes.coerceAtLeast(1L) * 60_000L
                premiumCache.put(name, status, ttlMillis, System.currentTimeMillis())
            }
            future.complete(status)
        }
        return future
    }

    private fun queryMojang(name: String): PremiumStatus {
        val timeout = configManager.current().premium.mojangApiTimeoutMs.coerceAtLeast(500L)
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeout))
            .build()
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$encodedName"))
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build()

        // In online-mode=false this only proves that the name exists on Mojang,
        // not that the current connection owns that premium account.
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return when (response.statusCode()) {
            200 -> PremiumStatus.PREMIUM
            204, 404 -> PremiumStatus.NOT_PREMIUM
            else -> PremiumStatus.FAILED
        }
    }
}
