package ym.authcode.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.util.GameProfile
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import ym.authcode.common.crypto.HmacSigner
import ym.authcode.common.crypto.NonceGenerator
import ym.authcode.common.identity.OfflineNameFailure
import ym.authcode.common.identity.OfflineNameResolver
import ym.authcode.common.identity.OfflineNameResult
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.common.model.ProxyAuthPayload
import ym.authcode.common.protocol.AuthCodeChannel
import ym.authcode.common.protocol.AuthCodePayloadCodec
import ym.authcode.velocity.config.VelocityConfigLoader
import ym.authcode.velocity.config.VelocityFailedAction
import ym.authcode.velocity.config.VelocitySettings
import ym.authcode.velocity.lang.VelocityLangManager
import ym.authcode.velocity.service.MojangNameStatus
import ym.authcode.velocity.service.MojangProfileService
import java.nio.file.Path
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Plugin(
    id = "authcode",
    name = "AuthCode",
    version = "1.0.0",
    description = "Velocity-side premium/offline assertion plugin for AuthCode.",
    authors = ["DreamStar"]
)
class AuthCodeVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @param:DataDirectory private val dataDirectory: Path
) {
    private lateinit var configLoader: VelocityConfigLoader
    private lateinit var lang: VelocityLangManager
    private lateinit var settings: VelocitySettings
    private lateinit var premiumService: MojangProfileService
    private lateinit var identifier: MinecraftChannelIdentifier
    private val pendingByName = ConcurrentHashMap<String, PendingLogin>()
    private val sessions = ConcurrentHashMap<UUID, ProxySession>()

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        configLoader = VelocityConfigLoader(dataDirectory, javaClass.classLoader)
        settings = configLoader.load()
        lang = VelocityLangManager(configLoader)
        lang.load()
        premiumService = MojangProfileService({ settings }, logger)
        identifier = MinecraftChannelIdentifier.from(settings.forward.channel)
        server.channelRegistrar.register(identifier)
        if (settings.requireVelocityOfflineMode && server.configuration.isOnlineMode) {
            logger.warn("AuthCode requires Velocity online-mode=false for hybrid premium/offline authentication.")
        }
        logger.info("AuthCode Velocity enabled on channel {}.", settings.forward.channel)
    }

    @Subscribe(priority = 100)
    fun onPreLogin(event: PreLoginEvent): EventTask {
        return EventTask.withContinuation { continuation ->
            if (!::settings.isInitialized) {
                event.result = PreLoginEvent.PreLoginComponentResult.denied(configError())
                continuation.resume()
                return@withContinuation
            }
            if (settings.requireVelocityOfflineMode && server.configuration.isOnlineMode) {
                event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.config-error"))
                continuation.resume()
                return@withContinuation
            }
            resolvePreLogin(event).whenComplete { _, throwable ->
                try {
                    if (throwable != null) {
                        logger.warn("AuthCode pre-login decision failed for {}: {}", event.username, throwable.message)
                        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.mojang-failed-deny"))
                    }
                    continuation.resume()
                } catch (exception: Throwable) {
                    continuation.resumeWithException(exception)
                }
            }
        }
    }

    @Subscribe(priority = 100)
    fun onGameProfileRequest(event: GameProfileRequestEvent) {
        if (!::settings.isInitialized || event.isOnlineMode) {
            return
        }
        val pending = pendingByName[event.username.lowercase(Locale.ROOT)] ?: return
        if (pending.expectedPremium) {
            return
        }
        val identity = pending.identity ?: return
        val profile = GameProfile(identity.uuid, identity.internalName, emptyList())
        val rewrittenIdentity = identity
        event.gameProfile = profile
        pendingByName[identity.internalName.lowercase(Locale.ROOT)] = pending.copy(identity = rewrittenIdentity)
        if (identity.internalName != identity.originalName) {
            logger.info(
                "AuthCode offline profile rewritten: originalName={}, internalName={}, displayName={}, uuid={}, premium=false",
                identity.originalName,
                identity.internalName,
                identity.displayName,
                rewrittenIdentity.uuid
            )
        }
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        val pending = pendingByName.remove(player.username.lowercase(Locale.ROOT))
        val premium = pending?.expectedPremium == true && player.isOnlineMode
        val verifiedAt = System.currentTimeMillis()
        val identity = if (premium) {
            OfflineNameResolver.premium(player.username, player.uniqueId)
        } else {
            pending?.identity?.copy(uuid = player.uniqueId) ?: PlayerIdentity(
                originalName = player.username,
                internalName = player.username,
                displayName = player.username,
                uuid = player.uniqueId,
                premium = false
            )
        }
        if (identity.originalName.lowercase(Locale.ROOT) != player.username.lowercase(Locale.ROOT)) {
            pendingByName.remove(identity.originalName.lowercase(Locale.ROOT))
        }
        sessions[player.uniqueId] = ProxySession(
            identity = identity.copy(
                premium = premium,
                verifiedAt = verifiedAt,
                authSource = "VELOCITY"
            ),
            remoteIp = player.remoteAddress.address.hostAddress ?: "unknown",
            loginTime = verifiedAt
        )
        logger.info(
            "AuthCode login identity: originalName={}, internalName={}, displayName={}, uuid={}, premium={}",
            identity.originalName,
            player.username,
            identity.displayName,
            player.uniqueId,
            premium
        )
        player.sendMessage(
            if (premium) {
                lang.render("velocity.premium-detected")
            } else {
                lang.render("velocity.offline-detected")
            }
        )
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val player = event.player
        val delayMillis = settings.forward.sendDelayTicks.coerceAtLeast(0L) * 50L
        server.scheduler.buildTask(this, Runnable {
            sendAssertion(player)
        }).delay(Duration.ofMillis(delayMillis)).schedule()
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        sessions.remove(event.player.uniqueId)
        pendingByName.remove(event.player.username.lowercase(Locale.ROOT))
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        sessions.clear()
        pendingByName.clear()
        if (::premiumService.isInitialized) {
            premiumService.clear()
        }
    }

    private fun handleLookupFailure(event: PreLoginEvent) {
        if (settings.premium.failedAction == VelocityFailedAction.REQUIRE_OFFLINE && settings.offline.allowOfflinePlayers) {
            forceOffline(event, resolveOfflineName(event))
            return
        }
        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.mojang-failed-deny"))
    }

    private fun resolvePreLogin(event: PreLoginEvent): CompletableFuture<Void> {
        if (!settings.premium.enabled || settings.premium.checkMode != "MOJANG_NAME_LOOKUP") {
            return prepareOffline(event)
        }
        return premiumService.checkName(event.username).thenCompose { status ->
            when (status) {
                MojangNameStatus.PREMIUM -> {
                    if (settings.security.denyPremiumNameOfflineSpoof) {
                        forceOnline(event)
                        completed()
                    } else {
                        prepareOffline(event)
                    }
                }
                MojangNameStatus.NOT_PREMIUM -> prepareOffline(event)
                MojangNameStatus.FAILED -> {
                    handleLookupFailure(event)
                    completed()
                }
            }
        }
    }

    private fun forceOnline(event: PreLoginEvent) {
        pendingByName[event.username.lowercase(Locale.ROOT)] = PendingLogin(
            expectedPremium = true,
            originalName = event.username
        )
        event.result = PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
    }

    private fun prepareOffline(event: PreLoginEvent): CompletableFuture<Void> {
        val result = resolveOfflineName(event)
        if (!result.success) {
            denyOfflineName(event, result.failure ?: OfflineNameFailure.INVALID_NAME)
            return completed()
        }
        if (!settings.offlineName.avoidPremiumInternalName || !settings.premium.enabled) {
            forceOffline(event, result)
            return completed()
        }
        val internalName = result.internalName ?: event.username
        if (internalName.equals(event.username, ignoreCase = true)) {
            forceOffline(event, result)
            return completed()
        }
        return premiumService.checkName(internalName).handle<Void> { status, throwable ->
            if (throwable != null || status == MojangNameStatus.FAILED) {
                event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.mojang-failed-deny"))
            } else if (status == MojangNameStatus.PREMIUM) {
                denyOfflineName(event, OfflineNameFailure.INTERNAL_NAME_CONFLICT)
            } else {
                forceOffline(event, result)
            }
            null
        }
    }

    private fun resolveOfflineName(event: PreLoginEvent): OfflineNameResult {
        return OfflineNameResolver.resolve(event.username, settings.offlineName)
    }

    private fun forceOffline(event: PreLoginEvent, result: OfflineNameResult) {
        if (!settings.offline.allowOfflinePlayers) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.config-error"))
            return
        }
        if (!result.success) {
            denyOfflineName(event, result.failure ?: OfflineNameFailure.INVALID_NAME)
            return
        }
        pendingByName[event.username.lowercase(Locale.ROOT)] = PendingLogin(
            expectedPremium = false,
            originalName = event.username,
            identity = result.identity()
        )
        event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
    }

    private fun denyOfflineName(event: PreLoginEvent, failure: OfflineNameFailure) {
        val key = when (failure) {
            OfflineNameFailure.INVALID_NAME -> "velocity.invalid-name"
            OfflineNameFailure.NAME_TOO_LONG -> "velocity.name-too-long"
            OfflineNameFailure.INTERNAL_NAME_CONFLICT -> "velocity.internal-name-conflict"
        }
        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render(key))
    }

    private fun sendAssertion(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val identity = session.identity
        val now = System.currentTimeMillis()
        val unsignedPayload = ProxyAuthPayload(
            version = AuthCodeChannel.VERSION,
            username = identity.internalName,
            uuid = identity.uuid.toString(),
            originalName = identity.originalName,
            internalName = identity.internalName,
            displayName = identity.displayName,
            premium = identity.premium,
            timestamp = now,
            nonce = NonceGenerator.generate(),
            signature = ""
        )
        val payload = HmacSigner.signPayload(settings.forward.secret, unsignedPayload)
        val bytes = AuthCodePayloadCodec.encode(payload)
        player.currentServer.ifPresent { connection ->
            val sent = connection.sendPluginMessage(identifier, bytes)
            if (!sent) {
                logger.warn("Failed to send AuthCode assertion for {}.", player.username)
            }
        }
    }

    private fun configError(): Component {
        return if (::lang.isInitialized) lang.render("velocity.config-error") else Component.empty()
    }

    private fun completed(): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }
}

private data class PendingLogin(
    val expectedPremium: Boolean,
    val originalName: String,
    val identity: PlayerIdentity? = null
)

private data class ProxySession(
    val identity: PlayerIdentity,
    val remoteIp: String,
    val loginTime: Long
)
