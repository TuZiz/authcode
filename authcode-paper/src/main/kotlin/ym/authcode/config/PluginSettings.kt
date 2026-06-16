package ym.authcode.config

import org.bukkit.configuration.file.FileConfiguration
import ym.authcode.common.identity.OfflineNameOverflowMode
import ym.authcode.common.identity.OfflineNameSettings
import ym.authcode.common.identity.OfflineUuidSource
import java.util.Locale

data class PluginSettings(
    val auth: AuthSettings,
    val proxy: ProxySettings,
    val premium: PremiumSettings,
    val offlineName: OfflineNameSettings,
    val identityDisplay: IdentityDisplaySettings,
    val offlineAuth: OfflineAuthSettings,
    val inviteCode: InviteCodeSettings,
    val lock: LockSettings,
    val commands: CommandSettings,
    val storage: StorageSettings,
    val language: LanguageSettings,
    val gui: GuiSettings
) {
    companion object {
        fun from(config: FileConfiguration): PluginSettings {
            return PluginSettings(
                auth = AuthSettings.from(config),
                proxy = ProxySettings.from(config),
                premium = PremiumSettings.from(config),
                offlineName = OfflineNameConfig.from(config),
                identityDisplay = IdentityDisplaySettings.from(config),
                offlineAuth = OfflineAuthSettings.from(config),
                inviteCode = InviteCodeSettings.from(config),
                lock = LockSettings.from(config),
                commands = CommandSettings.from(config),
                storage = StorageSettings.from(config),
                language = LanguageSettings.from(config),
                gui = GuiSettings.from(config)
            )
        }
    }
}

data class InviteCodeSettings(
    val defaultMaxUses: Int,
    val defaultExpireAfter: String?,
    val randomMinLength: Int,
    val randomMaxLength: Int,
    val randomDigitsOnly: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): InviteCodeSettings {
            val minLength = config.getInt("invite-code.random.min-length", 4).coerceIn(1, 32)
            val maxLength = config.getInt("invite-code.random.max-length", 6).coerceIn(minLength, 32)
            return InviteCodeSettings(
                defaultMaxUses = config.getInt("invite-code.defaults.max-uses", 1).coerceAtLeast(1),
                defaultExpireAfter = config.getString("invite-code.defaults.expire-after", "7d"),
                randomMinLength = minLength,
                randomMaxLength = maxLength,
                randomDigitsOnly = config.getBoolean("invite-code.random.digits-only", true)
            )
        }
    }
}

data class ProxySettings(
    val enabled: Boolean,
    val mode: ProxyMode,
    val channel: String,
    val secret: String,
    val payloadTtlSeconds: Long,
    val waitTimeoutSeconds: Long,
    val requireProxyAssertion: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): ProxySettings {
            return ProxySettings(
                enabled = config.getBoolean("proxy.enabled", true),
                mode = ProxyMode.parse(config.getString("proxy.mode")),
                channel = config.getString("proxy.channel", "authcode:auth") ?: "authcode:auth",
                secret = config.getString("proxy.secret", "change-this-random-long-secret")
                    ?: "change-this-random-long-secret",
                payloadTtlSeconds = config.getLong("proxy.payload-ttl-seconds", 10L),
                waitTimeoutSeconds = config.getLong("proxy.wait-timeout-seconds", 12L),
                requireProxyAssertion = config.getBoolean("proxy.require-proxy-assertion", true)
            )
        }
    }
}

enum class ProxyMode {
    NONE,
    VELOCITY_PROXY_PLUGIN,
    MOJANG_NAME_LOOKUP;

    companion object {
        fun parse(value: String?): ProxyMode {
            return entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: VELOCITY_PROXY_PLUGIN
        }
    }
}

data class AuthSettings(
    val authTimeoutSeconds: Long,
    val allowChatBeforeAuth: Boolean,
    val maxLoginAttempts: Int,
    val passwordMinLength: Int,
    val passwordMaxLength: Int,
    val kickOnTimeout: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): AuthSettings {
            return AuthSettings(
                authTimeoutSeconds = config.getLong("settings.auth-timeout-seconds", 120L),
                allowChatBeforeAuth = config.getBoolean("settings.allow-chat-before-auth", false),
                maxLoginAttempts = config.getInt("settings.max-login-attempts", 5),
                passwordMinLength = config.getInt("settings.password-min-length", 6),
                passwordMaxLength = config.getInt("settings.password-max-length", 32),
                kickOnTimeout = config.getBoolean("settings.kick-on-timeout", true)
            )
        }
    }
}

data class PremiumSettings(
    val autoPass: Boolean,
    val skipCode: Boolean,
    val skipPassword: Boolean,
    val checkMode: String,
    val failedAction: PremiumFailedAction,
    val mojangApiTimeoutMs: Long,
    val cacheMinutes: Long,
    val legacyMojangNameLookupEnabled: Boolean,
    val manualOverrideEnabled: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): PremiumSettings {
            return PremiumSettings(
                autoPass = config.getBoolean("premium.auto-pass", true),
                skipCode = config.getBoolean("premium.skip-code", true),
                skipPassword = config.getBoolean("premium.skip-password", true),
                checkMode = config.getString("premium.check-mode", "MOJANG_NAME_LOOKUP") ?: "MOJANG_NAME_LOOKUP",
                failedAction = PremiumFailedAction.parse(config.getString("premium.failed-action")),
                mojangApiTimeoutMs = config.getLong("premium.mojang-api-timeout-ms", 3000L),
                cacheMinutes = config.getLong("premium.cache-minutes", 60L),
                legacyMojangNameLookupEnabled = config.getBoolean(
                    "premium.legacy-mojang-name-lookup-enabled",
                    false
                ),
                manualOverrideEnabled = config.getBoolean("premium.manual-override-enabled", false)
            )
        }
    }
}

object OfflineNameConfig {
    fun from(config: FileConfiguration): OfflineNameSettings {
        return OfflineNameSettings(
            enabled = config.getBoolean("offline-name.enabled", true),
            prefix = config.getString("offline-name.prefix", "o_") ?: "o_",
            avoidDoublePrefix = config.getBoolean("offline-name.avoid-double-prefix", true),
            stripDisplayPrefix = config.getBoolean("offline-name.strip-display-prefix", true),
            maxNameLength = config.getInt("offline-name.max-name-length", 16).coerceIn(1, 16),
            overflowMode = OfflineNameOverflowMode.parse(config.getString("offline-name.overflow-mode", "HASH_SUFFIX")),
            hashLength = config.getInt("offline-name.hash-length", 4).coerceIn(1, 8),
            avoidPremiumInternalName = config.getBoolean("offline-name.avoid-premium-internal-name", true),
            uuidSource = OfflineUuidSource.parse(config.getString("offline-name.uuid-source", "PREFIXED_INTERNAL_NAME"))
        )
    }
}

data class IdentityDisplaySettings(
    val enabled: Boolean,
    val applyChat: Boolean,
    val applyTabList: Boolean,
    val applyPlayerDisplayName: Boolean,
    val applyJoinQuitMessage: Boolean,
    val stripInternalPrefix: Boolean,
    val placeholderOnly: Boolean,
    val applyPlaceholder: Boolean,
    val premium: IdentityTagStyle,
    val offline: IdentityTagStyle
) {
    val applyTab: Boolean
        get() = applyTabList

    val applyDisplayName: Boolean
        get() = applyPlayerDisplayName

    val premiumFormat: String
        get() = premium.format

    val offlineFormat: String
        get() = offline.format

    companion object {
        fun from(config: FileConfiguration): IdentityDisplaySettings {
            return IdentityDisplaySettings(
                enabled = config.getBoolean(
                    "display.identity-tag.enabled",
                    config.getBoolean("identity-display.enabled", true)
                ),
                applyChat = config.getBoolean(
                    "display.identity-tag.apply-chat",
                    config.getBoolean("identity-display.apply-chat", true)
                ),
                applyTabList = config.getBoolean(
                    "display.identity-tag.apply-tab-list",
                    config.getBoolean("identity-display.apply-tab", true)
                ),
                applyPlayerDisplayName = config.getBoolean(
                    "display.identity-tag.apply-player-display-name",
                    config.getBoolean("identity-display.apply-display-name", true)
                ),
                applyJoinQuitMessage = config.getBoolean("display.identity-tag.apply-join-quit-message", false),
                stripInternalPrefix = config.getBoolean(
                    "display.identity-tag.strip-internal-prefix",
                    config.getBoolean("offline-name.strip-display-prefix", true)
                ),
                placeholderOnly = config.getBoolean("display.identity-tag.placeholder-only", false),
                applyPlaceholder = config.getBoolean(
                    "display.identity-tag.apply-placeholder",
                    config.getBoolean("identity-display.apply-placeholder", true)
                ),
                premium = IdentityTagStyle.from(
                    config,
                    "display.identity-tag.premium",
                    "identity-display.premium-format",
                    "{identity_prefix} {display_name}"
                ),
                offline = IdentityTagStyle.from(
                    config,
                    "display.identity-tag.offline",
                    "identity-display.offline-format",
                    "{identity_prefix} {display_name}"
                )
            )
        }
    }
}

data class IdentityTagStyle(
    val enabled: Boolean,
    val prefix: String?,
    val nameColor: String,
    val format: String
) {
    companion object {
        fun from(
            config: FileConfiguration,
            path: String,
            legacyFormatPath: String,
            legacyDefaultFormat: String
        ): IdentityTagStyle {
            return IdentityTagStyle(
                enabled = config.getBoolean("$path.enabled", true),
                prefix = if (config.contains("$path.prefix")) config.getString("$path.prefix") else null,
                nameColor = config.getString("$path.name-color", "<white>") ?: "<white>",
                format = config.getString(
                    "$path.format",
                    config.getString(legacyFormatPath, legacyDefaultFormat)
                ) ?: legacyDefaultFormat
            )
        }
    }
}

data class OfflineAuthSettings(
    val requireCode: Boolean,
    val requireRegister: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): OfflineAuthSettings {
            return OfflineAuthSettings(
                requireCode = config.getBoolean("offline-auth.require-code", true),
                requireRegister = config.getBoolean("offline-auth.require-register", true)
            )
        }
    }
}

enum class PremiumFailedAction {
    REQUIRE_CODE,
    ALLOW,
    KICK;

    companion object {
        fun parse(value: String?): PremiumFailedAction {
            return entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: REQUIRE_CODE
        }
    }
}

data class LockSettings(
    val blockMove: Boolean,
    val blockBreak: Boolean,
    val blockPlace: Boolean,
    val blockAttack: Boolean,
    val blockInteract: Boolean,
    val blockDrop: Boolean,
    val blockPickup: Boolean,
    val blockInventory: Boolean,
    val blindness: Boolean,
    val slowness: Boolean
) {
    companion object {
        fun from(config: FileConfiguration): LockSettings {
            return LockSettings(
                blockMove = config.getBoolean("lock.block-move", true),
                blockBreak = config.getBoolean("lock.block-break", true),
                blockPlace = config.getBoolean("lock.block-place", true),
                blockAttack = config.getBoolean("lock.block-attack", true),
                blockInteract = config.getBoolean("lock.block-interact", true),
                blockDrop = config.getBoolean("lock.block-drop", true),
                blockPickup = config.getBoolean("lock.block-pickup", true),
                blockInventory = config.getBoolean("lock.block-inventory", true),
                blindness = config.getBoolean("lock.blindness", false),
                slowness = config.getBoolean("lock.slowness", true)
            )
        }
    }
}

data class CommandSettings(
    val allowedBeforeAuth: Set<String>
) {
    companion object {
        fun from(config: FileConfiguration): CommandSettings {
            return CommandSettings(
                allowedBeforeAuth = config.getStringList("commands.allowed-before-auth")
                    .map { it.trim().removePrefix("/").lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
    }
}

data class StorageSettings(
    val type: String,
    val sqliteFile: String
) {
    companion object {
        fun from(config: FileConfiguration): StorageSettings {
            return StorageSettings(
                type = config.getString("storage.type", "sqlite") ?: "sqlite",
                sqliteFile = config.getString("storage.sqlite-file", "authcode.db") ?: "authcode.db"
            )
        }
    }
}

data class LanguageSettings(
    val default: String,
    val file: String
) {
    companion object {
        fun from(config: FileConfiguration): LanguageSettings {
            return LanguageSettings(
                default = config.getString("language.default", "zh_cn") ?: "zh_cn",
                file = config.getString("language.file", "lang/zh_cn.yml") ?: "lang/zh_cn.yml"
            )
        }
    }
}

data class GuiSettings(
    val enabled: Boolean,
    val folder: String
) {
    companion object {
        fun from(config: FileConfiguration): GuiSettings {
            return GuiSettings(
                enabled = config.getBoolean("gui.enabled", true),
                folder = config.getString("gui.folder", "gui") ?: "gui"
            )
        }
    }
}
