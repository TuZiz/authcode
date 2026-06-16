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
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class MojangProfileService(
    private val settingsProvider: () -> VelocitySettings,
    private val logger: Logger
) {
    private val client = HttpClient.newBuilder().build()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun checkName(name: String): CompletableFuture<MojangNameStatus> {
        return fetchProfile(name).thenApply { it.status }
    }

    fun fetchProfile(name: String): CompletableFuture<MojangProfileLookup> {
        val now = System.currentTimeMillis()
        val key = name.lowercase(Locale.ROOT)
        cache[key]?.takeIf { it.expiresAt > now }?.let {
            return CompletableFuture.completedFuture(it.lookup)
        }
        val settings = settingsProvider().premium
        val timeout = settings.mojangApiTimeoutMs.coerceAtLeast(500L)
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$encodedName"))
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .handle { response, throwable ->
                if (throwable != null) {
                    logger.warn("Mojang API query failed for {}: {}", name, throwable.message)
                    return@handle MojangProfileLookup(MojangNameStatus.FAILED, null, null)
                }
                when (response.statusCode()) {
                    200 -> parseProfile(response.body())
                    204, 404 -> MojangProfileLookup(MojangNameStatus.NOT_PREMIUM, null, null)
                    else -> MojangProfileLookup(MojangNameStatus.FAILED, null, null)
                }
            }
            .thenApply { lookup ->
                if (lookup.status != MojangNameStatus.FAILED) {
                    val ttlMillis = settings.cacheMinutes.coerceAtLeast(1L) * 60_000L
                    cache[key] = CacheEntry(lookup, System.currentTimeMillis() + ttlMillis)
                }
                lookup
            }
    }

    fun clear() {
        cache.clear()
    }

    private fun parseProfile(body: String): MojangProfileLookup {
        val id = Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"").find(body)?.groupValues?.get(1)
            ?: return MojangProfileLookup(MojangNameStatus.FAILED, null, null)
        val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        val uuid = runCatching {
            UUID.fromString("${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-" +
                "${id.substring(16, 20)}-${id.substring(20, 32)}")
        }.getOrNull() ?: return MojangProfileLookup(MojangNameStatus.FAILED, null, null)
        return MojangProfileLookup(MojangNameStatus.PREMIUM, uuid, name)
    }
}

enum class MojangNameStatus {
    PREMIUM,
    NOT_PREMIUM,
    FAILED
}

data class MojangProfileLookup(
    val status: MojangNameStatus,
    val uuid: UUID?,
    val name: String?
)

private data class CacheEntry(
    val lookup: MojangProfileLookup,
    val expiresAt: Long
)
