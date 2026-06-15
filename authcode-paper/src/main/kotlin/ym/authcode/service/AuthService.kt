package ym.authcode.service

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.cache.AuthSessionCache
import ym.authcode.config.ConfigManager
import ym.authcode.config.PremiumFailedAction
import ym.authcode.config.ProxyMode
import ym.authcode.common.crypto.HmacSigner
import ym.authcode.common.model.ProxyAuthPayload
import ym.authcode.common.protocol.AuthCodeChannel
import ym.authcode.common.protocol.AuthCodePayloadCodec
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import ym.authcode.model.AuthState
import ym.authcode.model.InviteUseResult
import ym.authcode.model.PlayerAuthData
import ym.authcode.model.PremiumStatus
import ym.authcode.model.ProxyAuthLog
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.storage.Storage
import java.util.UUID

class AuthService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val storage: Storage,
    private val sessionCache: AuthSessionCache,
    private val proxyNonceTracker: ProxyNonceTracker,
    private val premiumCheckService: PremiumCheckService,
    private val inviteCodeService: InviteCodeService,
    private val passwordService: PasswordService,
    private val lockService: LockService,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) {
    fun handleJoin(player: Player) {
        val snapshot = PlayerSnapshot.from(player)
        val settings = configManager.current()
        val waitForProxy = settings.proxy.enabled && settings.proxy.mode == ProxyMode.VELOCITY_PROXY_PLUGIN

        sessionCache.start(snapshot.uuid)
        lockService.lock(player)
        lockService.scheduleTimeout(player)
        messageService.send(player, if (waitForProxy) "proxy.waiting" else "auth.join-checking")

        if (player.hasPermission("authcode.bypass")) {
            completeAuth(player, snapshot, "auth.already-authenticated")
            return
        }

        if (waitForProxy) {
            sessionCache.setState(snapshot.uuid, AuthState.CHECKING_PROXY)
            scheduleProxyAssertionTimeout(player, snapshot)
            return
        }

        storage.findPlayerByLowerName(snapshot.lowerName).whenComplete { data, throwable ->
            if (throwable != null) {
                handleDatabaseError(player, throwable)
                return@whenComplete
            }
            handlePremiumDecision(player, snapshot, data)
        }
    }

    fun handleProxyMessage(player: Player, channel: String, message: ByteArray) {
        val settings = configManager.current().proxy
        if (!settings.enabled || settings.mode != ProxyMode.VELOCITY_PROXY_PLUGIN || channel != settings.channel) {
            return
        }
        val snapshot = PlayerSnapshot.from(player)
        val payload = runCatching { AuthCodePayloadCodec.decode(message) }.getOrElse {
            plugin.logger.warning("Invalid Velocity payload for ${snapshot.name}: ${it.message}")
            rejectProxyPayload(player, snapshot, "proxy.invalid-signature-kick")
            return
        }
        val validationFailure = validateProxyPayload(snapshot, payload)
        if (validationFailure != null) {
            rejectProxyPayload(player, snapshot, validationFailure)
            return
        }
        acceptProxyPayload(player, snapshot, payload)
    }

    fun submitCode(player: Player, code: String) {
        val snapshot = PlayerSnapshot.from(player)
        when (sessionCache.state(snapshot.uuid)) {
            AuthState.NEED_CODE -> consumeInviteCode(player, snapshot, code)
            AuthState.WAITING_REGISTER -> messageService.send(player, "auth.need-register")
            AuthState.NEED_LOGIN -> messageService.send(player, "auth.already-registered")
            AuthState.CHECKING_PROXY -> messageService.send(player, "proxy.waiting")
            AuthState.CHECKING -> messageService.send(player, "auth.wait-checking")
            AuthState.AUTHENTICATED, null -> messageService.send(player, "auth.code-not-needed")
        }
    }

    fun register(player: Player, password: String, confirmPassword: String) {
        val snapshot = PlayerSnapshot.from(player)
        if (sessionCache.state(snapshot.uuid) != AuthState.WAITING_REGISTER) {
            messageService.send(player, "auth.need-register")
            return
        }
        if (!validateNewPassword(player, password, confirmPassword)) {
            return
        }
        val invitedByCode = sessionCache.invitedCode(snapshot.uuid) ?: run {
            messageService.send(player, "auth.need-code")
            return
        }
        passwordService.hash(password)
            .thenCompose { hash ->
                storage.saveRegisteredPlayer(
                    snapshot.uuid,
                    snapshot.name,
                    snapshot.lowerName,
                    hash,
                    invitedByCode,
                    snapshot.ip,
                    System.currentTimeMillis()
                )
            }
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    handleDatabaseError(player, throwable)
                    return@whenComplete
                }
                completeAuth(player, snapshot, "auth.register-success")
            }
    }

    fun login(player: Player, password: String) {
        val snapshot = PlayerSnapshot.from(player)
        if (sessionCache.state(snapshot.uuid) != AuthState.NEED_LOGIN) {
            messageService.send(player, "auth.need-login")
            return
        }
        storage.findPlayerByLowerName(snapshot.lowerName)
            .thenCompose { data ->
                val hash = data?.passwordHash
                if (hash == null || !data.registered) {
                    throw IllegalStateException("Player has no password hash")
                }
                passwordService.verify(password, hash)
            }
            .whenComplete { verified, throwable ->
                if (throwable != null) {
                    handleDatabaseError(player, throwable)
                    return@whenComplete
                }
                if (verified == true) {
                    updateLoginAndComplete(player, snapshot)
                } else {
                    failLogin(player, snapshot)
                }
            }
    }

    fun changePassword(player: Player, oldPassword: String, newPassword: String, confirmPassword: String) {
        val snapshot = PlayerSnapshot.from(player)
        if (sessionCache.state(snapshot.uuid) != AuthState.AUTHENTICATED) {
            messageService.send(player, "auth.not-authenticated")
            return
        }
        if (!validateNewPassword(player, newPassword, confirmPassword)) {
            return
        }
        storage.findPlayerByLowerName(snapshot.lowerName)
            .thenCompose { data ->
                val hash = data?.passwordHash
                if (hash == null || !data.registered || data.premium == true) {
                    throw UnsupportedOperationException("Password change is not available")
                }
                passwordService.verify(oldPassword, hash)
            }
            .thenCompose { verified ->
                if (!verified) {
                    throw SecurityException("Old password is invalid")
                }
                passwordService.hash(newPassword)
            }
            .thenCompose { hash ->
                storage.updatePassword(snapshot.lowerName, hash, System.currentTimeMillis())
            }
            .whenComplete { _, throwable ->
                when (throwable?.cause ?: throwable) {
                    null -> messageService.send(player, "auth.change-password-success")
                    is UnsupportedOperationException -> messageService.send(player, "auth.change-password-not-available")
                    is SecurityException -> messageService.send(player, "auth.old-password-invalid")
                    else -> handleDatabaseError(player, throwable)
                }
            }
    }

    private fun handlePremiumDecision(player: Player, snapshot: PlayerSnapshot, data: PlayerAuthData?) {
        val settings = configManager.current()
        if (settings.premium.manualOverrideEnabled) {
            if (data?.premium == true) {
                completePremium(player, snapshot)
                return
            }
            if (data?.premium == false) {
                continueWithoutPremium(player, snapshot, data)
                return
            }
        }
        val premiumSettings = settings.premium
        val legacyLookupEnabled = premiumSettings.autoPass &&
            (settings.proxy.mode == ProxyMode.MOJANG_NAME_LOOKUP ||
                premiumSettings.legacyMojangNameLookupEnabled ||
                (!settings.proxy.enabled && premiumSettings.checkMode == "MOJANG_NAME_LOOKUP"))
        if (!legacyLookupEnabled) {
            continueWithoutPremium(player, snapshot, data)
            return
        }
        premiumCheckService.checkName(snapshot.name).whenComplete { status, throwable ->
            if (throwable != null) {
                handlePremiumFailure(player, snapshot, data)
                return@whenComplete
            }
            when (status) {
                PremiumStatus.PREMIUM -> completePremium(player, snapshot)
                PremiumStatus.NOT_PREMIUM -> continueWithoutPremium(player, snapshot, data)
                PremiumStatus.FAILED -> handlePremiumFailure(player, snapshot, data)
            }
        }
    }

    private fun handlePremiumFailure(player: Player, snapshot: PlayerSnapshot, data: PlayerAuthData?) {
        when (configManager.current().premium.failedAction) {
            PremiumFailedAction.REQUIRE_CODE -> continueWithoutPremium(player, snapshot, data)
            PremiumFailedAction.ALLOW -> {
                messageService.send(player, "auth.premium-failed-allow")
                completeAuth(player, snapshot, "auth.already-authenticated")
            }
            PremiumFailedAction.KICK -> {
                messageService.kick(player, "auth.premium-failed-kick")
                sessionCache.remove(snapshot.uuid)
            }
        }
    }

    private fun continueWithoutPremium(player: Player, snapshot: PlayerSnapshot, data: PlayerAuthData?) {
        continueWithoutPremium(player, snapshot, data, "auth.need-code")
    }

    private fun continueWithoutPremium(
        player: Player,
        snapshot: PlayerSnapshot,
        data: PlayerAuthData?,
        codeMessageKey: String
    ) {
        if (data?.registered == true) {
            sessionCache.setState(snapshot.uuid, AuthState.NEED_LOGIN)
            messageService.send(player, "auth.need-login")
        } else {
            sessionCache.setState(snapshot.uuid, AuthState.NEED_CODE)
            messageService.send(player, codeMessageKey)
        }
    }

    private fun scheduleProxyAssertionTimeout(player: Player, snapshot: PlayerSnapshot) {
        val delay = configManager.current().proxy.waitTimeoutSeconds.coerceAtLeast(1L) * 20L
        scheduler.runAtEntityDelayed(player, delay) {
            if (sessionCache.state(snapshot.uuid) != AuthState.CHECKING_PROXY) {
                return@runAtEntityDelayed
            }
            if (configManager.current().proxy.requireProxyAssertion) {
                messageService.kick(player, "proxy.missing-assertion-kick")
                sessionCache.remove(snapshot.uuid)
                return@runAtEntityDelayed
            }
            storage.findPlayerByLowerName(snapshot.lowerName).whenComplete { data, throwable ->
                if (throwable != null) {
                    handleDatabaseError(player, throwable)
                    return@whenComplete
                }
                continueWithoutPremium(player, snapshot, data, "proxy.verified-offline")
            }
        }
    }

    private fun validateProxyPayload(snapshot: PlayerSnapshot, payload: ProxyAuthPayload): String? {
        val proxy = configManager.current().proxy
        val now = System.currentTimeMillis()
        val ttlMillis = proxy.payloadTtlSeconds.coerceAtLeast(1L) * 1000L
        if (payload.version != AuthCodeChannel.VERSION) {
            return "proxy.invalid-signature-kick"
        }
        if (!HmacSigner.verify(proxy.secret, payload)) {
            return "proxy.invalid-signature-kick"
        }
        if (payload.timestamp <= 0L || kotlin.math.abs(now - payload.timestamp) > ttlMillis) {
            return "proxy.expired-payload-kick"
        }
        if (!proxyNonceTracker.markIfNew(payload.nonce, now + ttlMillis, now)) {
            return "proxy.invalid-signature-kick"
        }
        if (payload.username != snapshot.name) {
            return "proxy.uuid-mismatch-kick"
        }
        val payloadUuid = runCatching { UUID.fromString(payload.uuid) }.getOrNull()
            ?: return "proxy.uuid-mismatch-kick"
        if (payloadUuid != snapshot.uuid) {
            return "proxy.uuid-mismatch-kick"
        }
        return null
    }

    private fun rejectProxyPayload(player: Player, snapshot: PlayerSnapshot, messageKey: String) {
        messageService.kick(player, messageKey)
        sessionCache.remove(snapshot.uuid)
    }

    private fun acceptProxyPayload(player: Player, snapshot: PlayerSnapshot, payload: ProxyAuthPayload) {
        val now = System.currentTimeMillis()
        val authSource = if (payload.premium) "VELOCITY_PREMIUM" else "VELOCITY_OFFLINE"
        storage.updateProxyAuthStatus(snapshot.uuid, snapshot.name, snapshot.lowerName, payload.premium, authSource, now)
            .thenCompose {
                storage.recordProxyAuthLog(
                    ProxyAuthLog(
                        uuid = snapshot.uuid,
                        name = snapshot.name,
                        premium = payload.premium,
                        authSource = authSource,
                        remoteIp = snapshot.ip,
                        serverName = plugin.server.name,
                        nonce = payload.nonce,
                        verifyTime = now,
                        createdAt = now
                    )
                )
            }
            .thenCompose {
                if (payload.premium) {
                    java.util.concurrent.CompletableFuture.completedFuture<PlayerAuthData?>(null)
                } else {
                    storage.findPlayerByLowerName(snapshot.lowerName)
                }
            }
            .whenComplete { data, throwable ->
                if (throwable != null) {
                    handleDatabaseError(player, throwable)
                    return@whenComplete
                }
                if (payload.premium) {
                    completeAuth(player, snapshot, "proxy.verified-premium")
                } else {
                    continueWithoutPremium(player, snapshot, data, "proxy.verified-offline")
                }
            }
    }

    private fun consumeInviteCode(player: Player, snapshot: PlayerSnapshot, code: String) {
        inviteCodeService.use(code, snapshot.uuid, snapshot.name, snapshot.ip).whenComplete { result, throwable ->
            if (throwable != null) {
                handleDatabaseError(player, throwable)
                return@whenComplete
            }
            when (result) {
                InviteUseResult.SUCCESS -> {
                    sessionCache.setState(snapshot.uuid, AuthState.WAITING_REGISTER)
                    sessionCache.setInvitedCode(snapshot.uuid, code)
                    messageService.send(player, "auth.code-success")
                }
                InviteUseResult.NOT_FOUND -> messageService.send(player, "auth.code-invalid")
                InviteUseResult.DISABLED -> messageService.send(player, "auth.code-disabled")
                InviteUseResult.EXPIRED -> messageService.send(player, "auth.code-expired")
                InviteUseResult.USED_UP -> messageService.send(player, "auth.code-used-up")
            }
        }
    }

    private fun updateLoginAndComplete(player: Player, snapshot: PlayerSnapshot) {
        storage.updateLogin(snapshot.lowerName, snapshot.ip, System.currentTimeMillis()).whenComplete { _, throwable ->
            if (throwable != null) {
                handleDatabaseError(player, throwable)
                return@whenComplete
            }
            completeAuth(player, snapshot, "auth.login-success")
        }
    }

    private fun failLogin(player: Player, snapshot: PlayerSnapshot) {
        val attempts = sessionCache.increaseAttempts(snapshot.uuid)
        val remaining = configManager.current().auth.maxLoginAttempts - attempts
        if (remaining <= 0) {
            messageService.kick(player, "auth.login-kick")
            sessionCache.remove(snapshot.uuid)
        } else {
            messageService.send(player, "auth.login-failed", mapOf("times" to remaining.toString()))
        }
    }

    private fun validateNewPassword(player: Player, password: String, confirmPassword: String): Boolean {
        val auth = configManager.current().auth
        if (password != confirmPassword) {
            messageService.send(player, "auth.password-not-match")
            return false
        }
        if (password.length < auth.passwordMinLength) {
            messageService.send(player, "auth.password-too-short", mapOf("min" to auth.passwordMinLength.toString()))
            return false
        }
        if (password.length > auth.passwordMaxLength) {
            messageService.send(player, "auth.password-too-long", mapOf("max" to auth.passwordMaxLength.toString()))
            return false
        }
        return true
    }

    private fun completePremium(player: Player, snapshot: PlayerSnapshot) {
        completeAuth(player, snapshot, "auth.premium-pass")
        messageService.send(player, "auth.premium-risk-warning")
    }

    private fun completeAuth(player: Player, snapshot: PlayerSnapshot, messageKey: String) {
        sessionCache.authenticate(snapshot.uuid)
        lockService.unlock(player)
        messageService.send(player, messageKey)
    }

    private fun handleDatabaseError(player: Player, throwable: Throwable) {
        plugin.logger.severe("Auth flow failed: ${throwable.message}")
        throwable.printStackTrace()
        messageService.send(player, LangKeys.DATABASE_ERROR)
    }
}
