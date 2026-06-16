package ym.authcode.velocity.config

import ym.authcode.common.identity.OfflineNameOverflowMode
import ym.authcode.common.identity.OfflineNameSettings
import ym.authcode.common.identity.OfflineUuidSource
import java.util.Locale

data class VelocitySettings(
    val requireVelocityOfflineMode: Boolean,
    val premium: VelocityPremiumSettings,
    val offline: VelocityOfflineSettings,
    val offlineName: OfflineNameSettings,
    val forward: VelocityForwardSettings,
    val security: VelocitySecuritySettings
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocitySettings {
            return VelocitySettings(
                requireVelocityOfflineMode = yaml.boolean("settings.require-velocity-offline-mode", true),
                premium = VelocityPremiumSettings.from(yaml),
                offline = VelocityOfflineSettings.from(yaml),
                offlineName = VelocityOfflineNameSettings.from(yaml),
                forward = VelocityForwardSettings.from(yaml),
                security = VelocitySecuritySettings.from(yaml)
            )
        }
    }
}

data class VelocityPremiumSettings(
    val enabled: Boolean,
    val checkMode: String,
    val mojangApiTimeoutMs: Long,
    val cacheMinutes: Long,
    val failedAction: VelocityFailedAction
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocityPremiumSettings {
            return VelocityPremiumSettings(
                enabled = yaml.boolean("premium.enabled", true),
                checkMode = yaml.string("premium.check-mode", "MOJANG_NAME_LOOKUP"),
                mojangApiTimeoutMs = yaml.long("premium.mojang-api-timeout-ms", 3000L),
                cacheMinutes = yaml.long("premium.cache-minutes", 60L),
                failedAction = VelocityFailedAction.parse(yaml.string("premium.failed-action", "REQUIRE_OFFLINE"))
            )
        }
    }
}

enum class VelocityFailedAction {
    REQUIRE_OFFLINE,
    DENY;

    companion object {
        fun parse(value: String): VelocityFailedAction {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: REQUIRE_OFFLINE
        }
    }
}

data class VelocityOfflineSettings(
    val allowOfflinePlayers: Boolean
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocityOfflineSettings {
            return VelocityOfflineSettings(
                allowOfflinePlayers = yaml.boolean("offline.allow-offline-players", true)
            )
        }
    }
}

object VelocityOfflineNameSettings {
    fun from(yaml: SimpleYaml): OfflineNameSettings {
        return OfflineNameSettings(
            enabled = yaml.boolean("offline-name.enabled", true),
            prefix = yaml.string("offline-name.prefix", "o_"),
            avoidDoublePrefix = yaml.boolean("offline-name.avoid-double-prefix", true),
            stripDisplayPrefix = yaml.boolean("offline-name.strip-display-prefix", true),
            maxNameLength = yaml.long("offline-name.max-name-length", 16L).toInt().coerceIn(1, 16),
            overflowMode = OfflineNameOverflowMode.parse(yaml.string("offline-name.overflow-mode", "HASH_SUFFIX")),
            hashLength = yaml.long("offline-name.hash-length", 4L).toInt().coerceIn(1, 8),
            avoidPremiumInternalName = yaml.boolean("offline-name.avoid-premium-internal-name", true),
            uuidSource = OfflineUuidSource.parse(
                yaml.string("offline-name.uuid-source", "PREFIXED_INTERNAL_NAME")
            )
        )
    }
}

data class VelocityForwardSettings(
    val channel: String,
    val secret: String,
    val payloadTtlSeconds: Long,
    val sendDelayTicks: Long
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocityForwardSettings {
            return VelocityForwardSettings(
                channel = yaml.string("forward.channel", "authcode:auth"),
                secret = yaml.string("forward.secret", "change-this-random-long-secret"),
                payloadTtlSeconds = yaml.long("forward.payload-ttl-seconds", 10L),
                sendDelayTicks = yaml.long("forward.send-delay-ticks", 2L)
            )
        }
    }
}

data class VelocitySecuritySettings(
    val denyPremiumNameOfflineSpoof: Boolean
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocitySecuritySettings {
            return VelocitySecuritySettings(
                denyPremiumNameOfflineSpoof = yaml.boolean("security.deny-premium-name-offline-spoof", true)
            )
        }
    }
}
