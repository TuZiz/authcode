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
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.api.util.GameProfile
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import ym.authcode.common.crypto.HmacSigner
import ym.authcode.common.crypto.NonceGenerator
import ym.authcode.common.identity.OfflineNameFailure
import ym.authcode.common.identity.OfflineNameResolver
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.common.model.ProxyAuthPayload
import ym.authcode.common.protocol.AuthCodeChannel
import ym.authcode.common.protocol.AuthCodePayloadCodec
import ym.authcode.velocity.command.AuthCodeVelocityCommand
import ym.authcode.velocity.config.UnknownNamePolicy
import ym.authcode.velocity.config.VelocityConfigLoader
import ym.authcode.velocity.config.VelocitySettings
import ym.authcode.velocity.lang.VelocityLangManager
import ym.authcode.velocity.service.MojangProfileService
import ym.authcode.velocity.storage.AuthProfile
import ym.authcode.velocity.storage.AuthProfileType
import ym.authcode.velocity.storage.PendingOfflineRename
import ym.authcode.velocity.storage.VelocityAuthStorage
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
    private lateinit var storage: VelocityAuthStorage
    private lateinit var identifier: MinecraftChannelIdentifier
    private val plansByKey = ConcurrentHashMap<String, LoginRoutePlan>()
    private val plansByName = ConcurrentHashMap<String, LoginRoutePlan>()
    private val plansByUuid = ConcurrentHashMap<UUID, LoginRoutePlan>()
    private val sessions = ConcurrentHashMap<UUID, ProxySession>()
    @Volatile
    private var storageReady = false
    private var cleanupTask: ScheduledTask? = null

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        configLoader = VelocityConfigLoader(dataDirectory, javaClass.classLoader)
        settings = configLoader.load()
        lang = VelocityLangManager(configLoader)
        lang.load()
        premiumService = MojangProfileService({ settings }, logger)
        storage = VelocityAuthStorage(dataDirectory, { settings }, logger)
        identifier = MinecraftChannelIdentifier.from(settings.forward.channel)
        server.channelRegistrar.register(identifier)
        if (settings.requireVelocityOfflineMode && server.configuration.isOnlineMode) {
            logger.warn("AuthCode requires Velocity online-mode=false for hybrid premium/offline authentication.")
        }
        storage.initialize().whenComplete { _, throwable ->
            if (throwable != null) {
                logger.error("AuthCode Velocity database initialization failed: {}", throwable.message)
                return@whenComplete
            }
            server.scheduler.buildTask(this, Runnable {
                storageReady = true
                registerCommand()
                scheduleCleanup()
                logger.info("AuthCode Velocity enabled on channel {}.", settings.forward.channel)
            }).schedule()
        }
    }

    @Subscribe(priority = 100)
    fun onPreLogin(event: PreLoginEvent): EventTask {
        return EventTask.withContinuation { continuation ->
            if (!readyForLogin()) {
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
                        logger.warn("AuthCode route decision failed for {}: {}", event.username, throwable.message)
                        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("velocity.config-error"))
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
        if (!readyForLogin() || event.isOnlineMode) {
            return
        }
        val plan = findPlan(event.connection, event.username) ?: return
        if (plan.premium) {
            return
        }
        event.gameProfile = GameProfile(plan.uuid, plan.internalName, emptyList())
        plansByName[plan.internalName.lowercase(Locale.ROOT)] = plan
        plansByUuid[plan.uuid] = plan
        logger.info(
            "AuthCode offline profile rewritten: originalName={}, internalName={}, displayName={}, uuid={}, route={}",
            plan.originalName,
            plan.internalName,
            plan.displayName,
            plan.uuid,
            plan.routeType
        )
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        if (!readyForLogin()) {
            event.player.disconnect(configError())
            return
        }
        val player = event.player
        val plan = removePlan(player) ?: fallbackOfflinePlan(player)
        if (plan.premium && !player.isOnlineMode) {
            player.disconnect(lang.render("same-name-login.premium-bound-kick"))
            return
        }
        if (!plan.premium && player.uniqueId != plan.uuid) {
            logger.warn(
                "AuthCode offline UUID mismatch for {}: expected={}, actual={}",
                player.username,
                plan.uuid,
                player.uniqueId
            )
            player.disconnect(lang.render("velocity.config-error"))
            return
        }
        val verifiedAt = System.currentTimeMillis()
        val originalName = if (plan.premium) player.username else plan.originalName
        val internalName = if (plan.premium) player.username else plan.internalName
        val displayName = if (plan.premium) player.username else plan.displayName
        val identity = PlayerIdentity(
            originalName = originalName,
            internalName = internalName,
            displayName = displayName,
            uuid = player.uniqueId,
            premium = plan.premium,
            verifiedAt = verifiedAt,
            authSource = "VELOCITY"
        )
        sessions[player.uniqueId] = ProxySession(
            identity = identity,
            remoteIp = player.remoteAddress.address?.hostAddress ?: player.remoteAddress.hostString,
            loginTime = verifiedAt
        )
        logger.info(
            "AuthCode login identity: originalName={}, internalName={}, displayName={}, uuid={}, premium={}, route={}",
            identity.originalName,
            identity.internalName,
            identity.displayName,
            identity.uuid,
            identity.premium,
            plan.routeType
        )
        player.sendMessage(
            if (identity.premium) {
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
        removePlan(event.player)
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        cleanupTask?.cancel()
        sessions.clear()
        plansByKey.clear()
        plansByName.clear()
        plansByUuid.clear()
        if (::storage.isInitialized) {
            storage.close()
        }
        if (::premiumService.isInitialized) {
            premiumService.clear()
        }
    }

    private fun resolvePreLogin(event: PreLoginEvent): CompletableFuture<Void> {
        val username = event.username
        if (!OfflineNameResolver.isValidMinecraftName(username)) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render("same-name-login.invalid-name"))
            return completed()
        }
        val sameName = settings.sameNameLogin
        if (sameName.blockClientReservedPrefix && username.startsWith(sameName.reservedPrefix, ignoreCase = true)) {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                lang.render("same-name-login.reserved-prefix", mapOf("reserved_prefix" to sameName.reservedPrefix))
            )
            return completed()
        }
        if (!sameName.enabled) {
            return routeUnknownOfflineDirect(event)
        }
        val usernameLower = username.lowercase(Locale.ROOT)
        return storage.findProfileByOriginalLower(usernameLower).thenCompose { profile ->
            when {
                profile != null && profile.authType == AuthProfileType.PREMIUM && profile.premiumBound -> {
                    routePremiumBound(event, profile)
                    completed()
                }
                profile != null && profile.authType == AuthProfileType.OFFLINE -> {
                    routeOfflineExisting(event, profile)
                    completed()
                }
                else -> routePendingOrUnknown(event)
            }
        }
    }

    private fun routePremiumBound(event: PreLoginEvent, profile: AuthProfile) {
        val plan = LoginRoutePlan(
            key = routeKey(event.connection, profile.originalNameLower),
            originalName = event.username,
            internalName = event.username,
            displayName = event.username,
            uuid = profile.uuid,
            premium = true,
            authType = AuthProfileType.PREMIUM,
            routeType = RouteType.PREMIUM_BOUND,
            createdAt = System.currentTimeMillis()
        )
        rememberPlan(plan)
        event.result = PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
    }

    private fun routeOfflineExisting(event: PreLoginEvent, profile: AuthProfile) {
        val plan = LoginRoutePlan(
            key = routeKey(event.connection, profile.originalNameLower),
            originalName = profile.originalName,
            internalName = profile.internalName,
            displayName = profile.displayName,
            uuid = profile.uuid,
            premium = false,
            authType = AuthProfileType.OFFLINE,
            routeType = RouteType.OFFLINE_EXISTING,
            createdAt = System.currentTimeMillis()
        )
        rememberPlan(plan)
        event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
    }

    private fun routePendingOrUnknown(event: PreLoginEvent): CompletableFuture<Void> {
        val usernameLower = event.username.lowercase(Locale.ROOT)
        val ip = event.connection.remoteAddress.address?.hostAddress ?: event.connection.remoteAddress.hostString
        val now = System.currentTimeMillis()
        return storage.findValidPending(usernameLower, ip, settings.sameNameLogin.pending.matchIp, now)
            .thenCompose { pending ->
                if (pending != null) {
                    routePendingConfirmed(event, pending, now)
                } else {
                    when (settings.sameNameLogin.unknownNamePolicy) {
                        UnknownNamePolicy.OFFLINE_PENDING -> createPendingAndDeny(event, ip, now)
                        UnknownNamePolicy.OFFLINE_DIRECT -> routeUnknownOfflineDirect(event)
                    }
                }
            }
    }

    private fun routePendingConfirmed(
        event: PreLoginEvent,
        pending: PendingOfflineRename,
        now: Long
    ): CompletableFuture<Void> {
        val uuid = offlineUuid(pending.username, pending.offlineName)
        return storage.confirmPendingAsOfflineProfile(pending, uuid, now).thenApply<Void> {
            val plan = LoginRoutePlan(
                key = routeKey(event.connection, it.originalNameLower),
                originalName = it.originalName,
                internalName = it.internalName,
                displayName = it.displayName,
                uuid = it.uuid,
                premium = false,
                authType = AuthProfileType.OFFLINE,
                routeType = RouteType.OFFLINE_PENDING_CONFIRMED,
                createdAt = now
            )
            rememberPlan(plan)
            event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
            null
        }
    }

    private fun createPendingAndDeny(event: PreLoginEvent, ip: String, now: Long): CompletableFuture<Void> {
        val result = OfflineNameResolver.resolve(event.username, settings.offlineName)
        if (!result.success) {
            denyOfflineName(event, result.failure ?: OfflineNameFailure.INVALID_NAME)
            return completed()
        }
        val offlineName = result.internalName ?: event.username
        val expiresAt = now + settings.sameNameLogin.pending.ttlSeconds * 1000L
        return storage.createPending(event.username, offlineName, ip, expiresAt, now).thenApply<Void> {
            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                lang.render("same-name-login.offline-pending-created", mapOf("offline_name" to offlineName))
            )
            null
        }
    }

    private fun routeUnknownOfflineDirect(event: PreLoginEvent): CompletableFuture<Void> {
        val result = OfflineNameResolver.resolve(event.username, settings.offlineName)
        if (!result.success) {
            denyOfflineName(event, result.failure ?: OfflineNameFailure.INVALID_NAME)
            return completed()
        }
        val identity = result.identity()
        val now = System.currentTimeMillis()
        return storage.upsertOfflineProfile(
            identity.originalName,
            identity.internalName,
            identity.displayName,
            identity.uuid,
            now
        ).thenApply<Void> {
            val plan = LoginRoutePlan(
                key = routeKey(event.connection, it.originalNameLower),
                originalName = it.originalName,
                internalName = it.internalName,
                displayName = it.displayName,
                uuid = it.uuid,
                premium = false,
                authType = AuthProfileType.OFFLINE,
                routeType = RouteType.OFFLINE_EXISTING,
                createdAt = now
            )
            rememberPlan(plan)
            event.result = PreLoginEvent.PreLoginComponentResult.forceOfflineMode()
            null
        }
    }

    private fun denyOfflineName(event: PreLoginEvent, failure: OfflineNameFailure) {
        val key = when (failure) {
            OfflineNameFailure.INVALID_NAME -> "same-name-login.invalid-name"
            OfflineNameFailure.NAME_TOO_LONG -> "same-name-login.offline-name-too-long"
            OfflineNameFailure.INTERNAL_NAME_CONFLICT -> "velocity.internal-name-conflict"
        }
        event.result = PreLoginEvent.PreLoginComponentResult.denied(lang.render(key))
    }

    private fun sendAssertion(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val identity = session.identity
        val now = System.currentTimeMillis()
        val authType = if (identity.premium) AuthProfileType.PREMIUM.name else AuthProfileType.OFFLINE.name
        val unsignedPayload = ProxyAuthPayload(
            version = AuthCodeChannel.VERSION,
            username = identity.internalName,
            uuid = identity.uuid.toString(),
            originalName = identity.originalName,
            internalName = identity.internalName,
            displayName = identity.displayName,
            premium = identity.premium,
            authType = authType,
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

    private fun rememberPlan(plan: LoginRoutePlan) {
        plansByKey[plan.key] = plan
        plansByName[plan.originalName.lowercase(Locale.ROOT)] = plan
        plansByName[plan.internalName.lowercase(Locale.ROOT)] = plan
        plansByUuid[plan.uuid] = plan
    }

    private fun findPlan(connection: InboundConnection, username: String): LoginRoutePlan? {
        val lowerName = username.lowercase(Locale.ROOT)
        return plansByKey[routeKey(connection, lowerName)] ?: plansByName[lowerName]
    }

    private fun removePlan(player: Player): LoginRoutePlan? {
        val byUuid = plansByUuid.remove(player.uniqueId)
        val byName = plansByName.remove(player.username.lowercase(Locale.ROOT))
        val plan = byUuid ?: byName
        if (plan != null) {
            plansByKey.remove(plan.key)
            plansByName.remove(plan.originalName.lowercase(Locale.ROOT))
            plansByName.remove(plan.internalName.lowercase(Locale.ROOT))
            plansByUuid.remove(plan.uuid)
        }
        return plan
    }

    private fun fallbackOfflinePlan(player: Player): LoginRoutePlan {
        val now = System.currentTimeMillis()
        return LoginRoutePlan(
            key = "fallback:${player.uniqueId}",
            originalName = player.username,
            internalName = player.username,
            displayName = player.username,
            uuid = player.uniqueId,
            premium = false,
            authType = AuthProfileType.OFFLINE,
            routeType = RouteType.OFFLINE_EXISTING,
            createdAt = now
        )
    }

    private fun routeKey(connection: InboundConnection, originalNameLower: String): String {
        return "${connection.remoteAddress}|$originalNameLower"
    }

    private fun offlineUuid(originalName: String, internalName: String): UUID {
        return OfflineNameResolver.offlineUuid(settings.offlineName.uuidSource.nameSource(originalName, internalName))
    }

    private fun scheduleCleanup() {
        cleanupTask?.cancel()
        cleanupTask = server.scheduler.buildTask(this, Runnable {
            val now = System.currentTimeMillis()
            cleanupPlans(now)
            storage.cleanupPending(now)
        }).repeat(Duration.ofSeconds(settings.sameNameLogin.pending.cleanupIntervalSeconds)).schedule()
    }

    private fun cleanupPlans(now: Long) {
        val maxAgeMillis = settings.sameNameLogin.pending.ttlSeconds.coerceAtLeast(120L) * 1000L
        plansByKey.values
            .filter { now - it.createdAt > maxAgeMillis }
            .forEach { plan ->
                plansByKey.remove(plan.key)
                plansByName.remove(plan.originalName.lowercase(Locale.ROOT))
                plansByName.remove(plan.internalName.lowercase(Locale.ROOT))
                plansByUuid.remove(plan.uuid)
            }
    }

    private fun registerCommand() {
        val command = AuthCodeVelocityCommand(
            settingsProvider = { settings },
            storage = storage,
            mojangProfileService = premiumService,
            lang = lang,
            identityLookup = { name ->
                val lower = name.lowercase(Locale.ROOT)
                sessions.values.firstOrNull {
                    it.identity.internalName.equals(name, ignoreCase = true) ||
                        it.identity.originalName.equals(name, ignoreCase = true) ||
                        it.identity.displayName.equals(name, ignoreCase = true) ||
                        it.identity.uuid.toString().lowercase(Locale.ROOT) == lower
                }?.identity
            }
        )
        val meta = server.commandManager.metaBuilder("authcode")
            .aliases("acode")
            .plugin(this)
            .build()
        server.commandManager.register(meta, command)
    }

    private fun readyForLogin(): Boolean {
        return ::settings.isInitialized && ::storage.isInitialized && storageReady
    }

    private fun configError(): Component {
        return if (::lang.isInitialized) lang.render("velocity.config-error") else Component.empty()
    }

    private fun completed(): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }
}

private data class LoginRoutePlan(
    val key: String,
    val originalName: String,
    val internalName: String,
    val displayName: String,
    val uuid: UUID,
    val premium: Boolean,
    val authType: AuthProfileType,
    val routeType: RouteType,
    val createdAt: Long
)

private enum class RouteType {
    PREMIUM_BOUND,
    OFFLINE_EXISTING,
    OFFLINE_PENDING_CONFIRMED
}

private data class ProxySession(
    val identity: PlayerIdentity,
    val remoteIp: String,
    val loginTime: Long
)
