package ym.authcode.velocity.service

import org.slf4j.Logger
import ym.authcode.velocity.config.VelocitySettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class MojangProfileService(
    private val settingsProvider: () -> VelocitySettings,
    private val logger: Logger
) {
    private val client = HttpClient.newBuilder().build()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun checkName(name: String): CompletableFuture<MojangNameStatus> {
        val now = System.currentTimeMillis()
        val key = name.lowercase(Locale.ROOT)
        cache[key]?.takeIf { it.expiresAt > now }?.let {
            return CompletableFuture.completedFuture(it.status)
        }
        val settings = settingsProvider().premium
        val timeout = settings.mojangApiTimeoutMs.coerceAtLeast(500L)
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$encodedName"))
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .handle { response, throwable ->
                if (throwable != null) {
                    logger.warn("Mojang API query failed for {}: {}", name, throwable.message)
                    return@handle MojangNameStatus.FAILED
                }
                when (response.statusCode()) {
                    200 -> MojangNameStatus.PREMIUM
                    204, 404 -> MojangNameStatus.NOT_PREMIUM
                    else -> MojangNameStatus.FAILED
                }
            }
            .thenApply { status ->
                if (status != MojangNameStatus.FAILED) {
                    val ttlMillis = settings.cacheMinutes.coerceAtLeast(1L) * 60_000L
                    cache[key] = CacheEntry(status, System.currentTimeMillis() + ttlMillis)
                }
                status
            }
    }

    fun clear() {
        cache.clear()
    }
}

enum class MojangNameStatus {
    PREMIUM,
    NOT_PREMIUM,
    FAILED
}

private data class CacheEntry(
    val status: MojangNameStatus,
    val expiresAt: Long
)
