package ym.authcode.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import ym.authcode.common.crypto.HmacSigner
import ym.authcode.common.crypto.NonceGenerator
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
            if (!settings.premium.enabled || settings.premium.checkMode != "MOJANG_NAME_LOOKUP") {
                forceOffline(event)
                continuation.resume()
                return@withContinuation
            }
            premiumService.checkName(event.username).whenComplete { status, throwable ->
                try {
                    if (throwable != null) {
                        handleLookupFailure(event)
                    } else {
                        handleLookupResult(event, status)
                    }
                    continuation.resume()
                } catch (exception: Throwable) {
                    continuation.resumeWithException(exception)
                }
            }
        }
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        val pending = pendingByName.remove(player.username.lowercase(Locale.ROOT))
        val premium = pending?.expectedPremium == true && player.isOnlineMode
        sessions[player.uniqueId] = ProxySession(
            username = player.username,
            premium = premium,
            remoteIp = player.remoteAddress.address.hostAddress ?: "unknown",
            loginTime = System.currentTimeMillis()
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

    private fun handleLookupResult(event: PreLoginEvent, status: MojangNameStatus) {
        when (status) {
            MojangNameStatus.PREMIUM -> forceOnline(event)
            MojangNameStatus.NOT_PREMIUM -> forceOffline(event)
            MojangNameStatus.FAILED -> handleLookupFailure(event)
        }
    }

    private fun handleLookupFailure(event: PreLoginEvent) {
        if (settings.premium.failedAction == VelocityFailedAction.REQUIRE_OFFLINE && settings.offline.allowOfflinePlayers) {
            forceOffline(event)
            return
        }
        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.mojang-failed-deny"))
    }

    private fun forceOnline(event: PreLoginEvent) {
        pendingByName[event.username.lowercase(Locale.ROOT)] = PendingLogin(expectedPremium = true)
        event.result = PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
    }

    private fun forceOffline(event: PreLoginEvent) {
        if (!settings.offline.allowOfflinePlayers) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.config-error"))
            return
        }
        pendingByName[event.username.lowercase(Locale.ROOT)] = PendingLogin(expectedPremium = false)
        event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
    }

    private fun sendAssertion(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val now = System.currentTimeMillis()
        val unsignedPayload = ProxyAuthPayload(
            version = AuthCodeChannel.VERSION,
            username = session.username,
            uuid = player.uniqueId.toString(),
            premium = session.premium,
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
}

private data class PendingLogin(
    val expectedPremium: Boolean
)

private data class ProxySession(
    val username: String,
    val premium: Boolean,
    val remoteIp: String,
    val loginTime: Long
)
