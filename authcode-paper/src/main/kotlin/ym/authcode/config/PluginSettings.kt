package ym.authcode.config

import org.bukkit.configuration.file.FileConfiguration
import java.util.Locale

data class PluginSettings(
    val auth: AuthSettings,
    val proxy: ProxySettings,
    val premium: PremiumSettings,
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
                lock = LockSettings.from(config),
                commands = CommandSettings.from(config),
                storage = StorageSettings.from(config),
                language = LanguageSettings.from(config),
                gui = GuiSettings.from(config)
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
                waitTimeoutSeconds = config.getLong("proxy.wait-timeout-seconds", 5L),
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
                enabled = config.getBoolean("gui.enabled", false),
                folder = config.getString("gui.folder", "gui") ?: "gui"
            )
        }
    }
}
