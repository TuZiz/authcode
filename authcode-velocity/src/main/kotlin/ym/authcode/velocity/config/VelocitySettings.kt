package ym.authcode.velocity.config

import ym.authcode.common.identity.OfflineNameOverflowMode
import ym.authcode.common.identity.OfflineNameSettings
import ym.authcode.common.identity.OfflineUuidSource
import java.util.Locale

data class VelocitySettings(
    val requireVelocityOfflineMode: Boolean,
    val sameNameLogin: SameNameLoginSettings,
    val premium: VelocityPremiumSettings,
    val offline: VelocityOfflineSettings,
    val offlineName: OfflineNameSettings,
    val forward: VelocityForwardSettings,
    val security: VelocitySecuritySettings,
    val storage: VelocityStorageSettings
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocitySettings {
            return VelocitySettings(
                requireVelocityOfflineMode = yaml.boolean("settings.require-velocity-offline-mode", true),
                sameNameLogin = SameNameLoginSettings.from(yaml),
                premium = VelocityPremiumSettings.from(yaml),
                offline = VelocityOfflineSettings.from(yaml),
                offlineName = VelocityOfflineNameSettings.from(yaml),
                forward = VelocityForwardSettings.from(yaml),
                security = VelocitySecuritySettings.from(yaml),
                storage = VelocityStorageSettings.from(yaml)
            )
        }
    }
}

data class SameNameLoginSettings(
    val enabled: Boolean,
    val routeMode: SameNameRouteMode,
    val defaultRoute: SameNameDefaultRoute,
    val premiumHosts: Set<String>,
    val offlineHosts: Set<String>,
    val allowDualProfileSameOriginalName: Boolean,
    val unknownPremiumPolicy: UnknownPremiumPolicy,
    val unknownOfflinePolicy: UnknownOfflinePolicy,
    val blockClientReservedPrefix: Boolean,
    val reservedPrefix: String,
    val pending: SameNamePendingSettings
) {
    companion object {
        fun from(yaml: SimpleYaml): SameNameLoginSettings {
            return SameNameLoginSettings(
                enabled = yaml.boolean("same-name-login.enabled", true),
                routeMode = SameNameRouteMode.parse(yaml.string("same-name-login.route-mode", "DATABASE_FIRST")),
                defaultRoute = SameNameDefaultRoute.parse(yaml.string("same-name-login.default-route", "DENY")),
                premiumHosts = yaml.stringList("same-name-login.premium-hosts").mapNotNull { normalizeHost(it) }.toSet(),
                offlineHosts = yaml.stringList("same-name-login.offline-hosts").mapNotNull { normalizeHost(it) }.toSet(),
                allowDualProfileSameOriginalName = yaml.boolean(
                    "same-name-login.allow-dual-profile-same-original-name",
                    true
                ),
                unknownPremiumPolicy = UnknownPremiumPolicy.parse(
                    yaml.string("same-name-login.unknown-premium-policy.mode", "AUTO_MOJANG_BIND")
                ),
                unknownOfflinePolicy = UnknownOfflinePolicy.parse(
                    yaml.string(
                        "same-name-login.unknown-offline-policy.mode",
                        yaml.string("same-name-login.unknown-name-policy", "OFFLINE_PENDING")
                    )
                ),
                blockClientReservedPrefix = yaml.boolean("same-name-login.block-client-reserved-prefix", true),
                reservedPrefix = yaml.string("same-name-login.reserved-prefix", yaml.string("offline-name.prefix", "o_")),
                pending = SameNamePendingSettings.from(yaml)
            )
        }

        private fun normalizeHost(value: String): String? {
            val trimmed = value.trim()
            if (trimmed.isBlank()) {
                return null
            }
            return trimmed
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .trim()
                .trimEnd('.')
                .lowercase(Locale.ROOT)
                .ifBlank { null }
        }
    }
}

data class SameNamePendingSettings(
    val ttlSeconds: Long,
    val matchIp: Boolean,
    val cleanupIntervalSeconds: Long
) {
    companion object {
        fun from(yaml: SimpleYaml): SameNamePendingSettings {
            return SameNamePendingSettings(
                ttlSeconds = yaml.long("same-name-login.pending.ttl-seconds", 120L).coerceAtLeast(10L),
                matchIp = yaml.boolean("same-name-login.pending.match-ip", true),
                cleanupIntervalSeconds = yaml.long(
                    "same-name-login.pending.cleanup-interval-seconds",
                    60L
                ).coerceAtLeast(10L)
            )
        }
    }
}

enum class SameNameRouteMode {
    DATABASE_FIRST,
    VIRTUAL_HOST;

    companion object {
        fun parse(value: String): SameNameRouteMode {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: DATABASE_FIRST
        }
    }
}

enum class SameNameDefaultRoute {
    PREMIUM,
    OFFLINE,
    DENY;

    companion object {
        fun parse(value: String): SameNameDefaultRoute {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: DENY
        }
    }
}

enum class UnknownPremiumPolicy {
    AUTO_MOJANG_BIND,
    ADMIN_BIND_ONLY;

    companion object {
        fun parse(value: String): UnknownPremiumPolicy {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: AUTO_MOJANG_BIND
        }
    }
}

enum class UnknownOfflinePolicy {
    OFFLINE_PENDING,
    OFFLINE_DIRECT;

    companion object {
        fun parse(value: String): UnknownOfflinePolicy {
            return entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: OFFLINE_PENDING
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
            overflowMode = OfflineNameOverflowMode.parse(yaml.string("offline-name.overflow-mode", "KICK")),
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

data class VelocityStorageSettings(
    val sqliteFile: String
) {
    companion object {
        fun from(yaml: SimpleYaml): VelocityStorageSettings {
            return VelocityStorageSettings(
                sqliteFile = yaml.string("storage.sqlite-file", "authcode.db")
            )
        }
    }
}
