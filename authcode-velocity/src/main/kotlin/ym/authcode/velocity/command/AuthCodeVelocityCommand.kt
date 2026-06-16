package ym.authcode.velocity.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import ym.authcode.common.identity.OfflineNameFailure
import ym.authcode.common.identity.OfflineNameResolver
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.velocity.config.VelocitySettings
import ym.authcode.velocity.lang.VelocityLangManager
import ym.authcode.velocity.service.MojangNameStatus
import ym.authcode.velocity.service.MojangProfileService
import ym.authcode.velocity.storage.AuthProfile
import ym.authcode.velocity.storage.VelocityAuthStorage
import java.util.Locale

class AuthCodeVelocityCommand(
    private val settingsProvider: () -> VelocitySettings,
    private val storage: VelocityAuthStorage,
    private val mojangProfileService: MojangProfileService,
    private val lang: VelocityLangManager,
    private val identityLookup: (String) -> PlayerIdentity?
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val args = invocation.arguments().toList()
        if (args.isEmpty()) {
            send(source, "velocity.admin.help")
            return
        }
        when (args[0].lowercase(Locale.ROOT)) {
            "premium" -> premium(source, args.drop(1))
            "profile" -> profile(source, args.drop(1))
            "namecheck" -> namecheck(source, args.drop(1))
            "pending" -> pending(source, args.drop(1))
            "identity" -> identity(source, args.drop(1))
            else -> send(source, "velocity.admin.unknown")
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val args = invocation.arguments()
        if (args.size <= 1) {
            return complete(args.getOrNull(0), listOf("premium", "profile", "namecheck", "pending", "identity"))
        }
        return when (args[0].lowercase(Locale.ROOT)) {
            "premium" -> complete(args.getOrNull(1), listOf("bind", "unbind", "info"))
            "pending" -> complete(args.getOrNull(1), listOf("list", "clear"))
            else -> emptyList()
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("authcode.admin") ||
            invocation.source().hasPermission("authcode.velocity.admin")
    }

    private fun premium(source: CommandSource, args: List<String>) {
        if (args.size != 2 || args[0].lowercase(Locale.ROOT) !in setOf("bind", "unbind", "info")) {
            send(source, "velocity.admin.usage-premium")
            return
        }
        val action = args[0].lowercase(Locale.ROOT)
        val name = args[1]
        when (action) {
            "bind" -> bindPremium(source, name)
            "unbind" -> unbindPremium(source, name)
            "info" -> profile(source, listOf(name))
        }
    }

    private fun bindPremium(source: CommandSource, name: String) {
        if (!validClientName(source, name)) {
            return
        }
        mojangProfileService.fetchProfile(name).thenCompose { lookup ->
            if (lookup.status == MojangNameStatus.PREMIUM && lookup.uuid != null) {
                storage.bindPremiumProfile(lookup.name ?: name, lookup.uuid, System.currentTimeMillis())
            } else {
                throw PremiumBindException(lookup.status)
            }
        }.whenComplete { profile, throwable ->
            val cause = throwable?.cause ?: throwable
            when (cause) {
                null -> send(
                    source,
                    "velocity.admin.premium-bind-success",
                    mapOf("name" to profile.originalName, "uuid" to profile.uuid.toString())
                )
                is PremiumBindException -> {
                    val key = if (cause.status == MojangNameStatus.NOT_PREMIUM) {
                        "velocity.admin.premium-bind-mojang-missing"
                    } else {
                        "velocity.admin.premium-bind-mojang-failed"
                    }
                    send(source, key, mapOf("name" to name))
                }
                else -> send(source, "velocity.admin.database-error")
            }
        }
    }

    private fun unbindPremium(source: CommandSource, name: String) {
        storage.unbindPremiumProfile(name, System.currentTimeMillis()).whenComplete { removed, throwable ->
            if (throwable != null) {
                send(source, "velocity.admin.database-error")
                return@whenComplete
            }
            if (removed == true) {
                send(source, "velocity.admin.premium-unbind-success", mapOf("name" to name))
            } else {
                send(source, "velocity.admin.profile-not-found", mapOf("name" to name))
            }
        }
    }

    private fun profile(source: CommandSource, args: List<String>) {
        if (args.size != 1) {
            send(source, "velocity.admin.usage-profile")
            return
        }
        storage.findProfileByName(args[0]).whenComplete { profile, throwable ->
            if (throwable != null) {
                send(source, "velocity.admin.database-error")
                return@whenComplete
            }
            if (profile == null) {
                send(source, "velocity.admin.profile-not-found", mapOf("name" to args[0]))
                return@whenComplete
            }
            sendProfile(source, profile)
        }
    }

    private fun namecheck(source: CommandSource, args: List<String>) {
        if (args.size != 1) {
            send(source, "velocity.admin.usage-namecheck")
            return
        }
        val name = args[0]
        val settings = settingsProvider()
        val valid = OfflineNameResolver.isValidMinecraftName(name)
        val reserved = settings.sameNameLogin.blockClientReservedPrefix &&
            name.startsWith(settings.sameNameLogin.reservedPrefix, ignoreCase = true)
        val resolved = OfflineNameResolver.resolve(name, settings.offlineName)
        val offlineName = resolved.internalName ?: ""
        val displayName = resolved.displayName ?: ""
        val tooLong = resolved.failure == OfflineNameFailure.NAME_TOO_LONG
        storage.findProfileByName(name).whenComplete { profile, throwable ->
            if (throwable != null) {
                send(source, "velocity.admin.database-error")
                return@whenComplete
            }
            val variables = mapOf(
                "name" to name,
                "valid" to bool(valid),
                "reserved" to bool(reserved),
                "has_profile" to bool(profile != null),
                "premium_bound" to bool(profile?.premiumBound == true),
                "offline_name" to offlineName,
                "overflow" to bool(tooLong),
                "display_name" to displayName
            )
            send(source, "velocity.admin.namecheck-result", variables)
        }
    }

    private fun pending(source: CommandSource, args: List<String>) {
        if (args.isEmpty()) {
            send(source, "velocity.admin.usage-pending")
            return
        }
        when (args[0].lowercase(Locale.ROOT)) {
            "list" -> {
                storage.listPending(System.currentTimeMillis()).whenComplete { pending, throwable ->
                    if (throwable != null) {
                        send(source, "velocity.admin.database-error")
                        return@whenComplete
                    }
                    if (pending.isNullOrEmpty()) {
                        send(source, "velocity.admin.pending-empty")
                        return@whenComplete
                    }
                    send(source, "velocity.admin.pending-header")
                    pending.forEach {
                        send(
                            source,
                            "velocity.admin.pending-line",
                            mapOf(
                                "name" to it.username,
                                "offline_name" to it.offlineName,
                                "ip" to it.ip,
                                "expires_at" to it.expiresAt.toString()
                            )
                        )
                    }
                }
            }
            "clear" -> {
                if (args.size != 2) {
                    send(source, "velocity.admin.usage-pending")
                    return
                }
                storage.clearPending(args[1]).whenComplete { count, throwable ->
                    if (throwable != null) {
                        send(source, "velocity.admin.database-error")
                    } else {
                        send(source, "velocity.admin.pending-cleared", mapOf("name" to args[1], "count" to count.toString()))
                    }
                }
            }
            else -> send(source, "velocity.admin.usage-pending")
        }
    }

    private fun identity(source: CommandSource, args: List<String>) {
        if (args.size != 1) {
            send(source, "velocity.admin.usage-identity")
            return
        }
        val identity = identityLookup(args[0])
        if (identity == null) {
            send(source, "velocity.admin.identity-not-found", mapOf("name" to args[0]))
            return
        }
        send(
            source,
            "velocity.admin.identity-result",
            mapOf(
                "original_name" to identity.originalName,
                "internal_name" to identity.internalName,
                "display_name" to identity.displayName,
                "uuid" to identity.uuid.toString(),
                "premium" to bool(identity.premium),
                "auth_source" to identity.authSource
            )
        )
    }

    private fun sendProfile(source: CommandSource, profile: AuthProfile) {
        send(
            source,
            "velocity.admin.profile-result",
            mapOf(
                "original_name" to profile.originalName,
                "internal_name" to profile.internalName,
                "display_name" to profile.displayName,
                "uuid" to profile.uuid.toString(),
                "auth_type" to profile.authType.name,
                "premium_bound" to bool(profile.premiumBound)
            )
        )
    }

    private fun validClientName(source: CommandSource, name: String): Boolean {
        if (!OfflineNameResolver.isValidMinecraftName(name)) {
            send(source, "same-name-login.invalid-name")
            return false
        }
        val prefix = settingsProvider().sameNameLogin.reservedPrefix
        if (name.startsWith(prefix, ignoreCase = true)) {
            send(source, "same-name-login.reserved-prefix", mapOf("reserved_prefix" to prefix))
            return false
        }
        return true
    }

    private fun send(source: CommandSource, key: String, variables: Map<String, String> = emptyMap()) {
        source.sendMessage(lang.render(key, variables))
    }

    private fun bool(value: Boolean): String {
        return if (value) lang.plain("velocity.admin.boolean-true") else lang.plain("velocity.admin.boolean-false")
    }

    private fun complete(input: String?, values: List<String>): List<String> {
        val prefix = input ?: ""
        return values.filter { it.startsWith(prefix, ignoreCase = true) }
    }
}

private class PremiumBindException(
    val status: MojangNameStatus
) : RuntimeException()
