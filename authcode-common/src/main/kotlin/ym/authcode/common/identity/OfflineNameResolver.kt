package ym.authcode.common.identity

import ym.authcode.common.model.PlayerIdentity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object OfflineNameResolver {
    private val validName = Regex("^[A-Za-z0-9_]{1,16}$")

    fun resolve(originalName: String, settings: OfflineNameSettings): OfflineNameResult {
        val normalized = originalName.trim()
        if (!isValidMinecraftName(normalized)) {
            return OfflineNameResult.failure(OfflineNameFailure.INVALID_NAME)
        }

        val displayName = displayName(normalized, settings)
        val baseInternalName = if (!settings.enabled) {
            normalized
        } else if (settings.avoidDoublePrefix && normalized.startsWith(settings.prefix)) {
            normalized
        } else {
            settings.prefix + normalized
        }

        val internalName = fitInternalName(baseInternalName, normalized, settings)
            ?: return OfflineNameResult.failure(OfflineNameFailure.NAME_TOO_LONG)

        if (!isValidMinecraftName(internalName)) {
            return OfflineNameResult.failure(OfflineNameFailure.INVALID_NAME)
        }

        return OfflineNameResult.success(
            originalName = normalized,
            internalName = internalName,
            displayName = displayName,
            uuid = offlineUuid(settings.uuidSource.nameSource(normalized, internalName))
        )
    }

    fun premium(originalName: String, uuid: UUID): PlayerIdentity {
        return PlayerIdentity(
            originalName = originalName,
            internalName = originalName,
            displayName = originalName,
            uuid = uuid,
            premium = true
        )
    }

    fun isValidMinecraftName(name: String): Boolean {
        return validName.matches(name)
    }

    fun offlineUuid(name: String): UUID {
        return UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8))
    }

    private fun displayName(originalName: String, settings: OfflineNameSettings): String {
        if (!settings.enabled || !settings.avoidDoublePrefix || !settings.stripDisplayPrefix) {
            return originalName
        }
        if (!originalName.startsWith(settings.prefix)) {
            return originalName
        }
        val stripped = originalName.removePrefix(settings.prefix)
        return stripped.ifBlank { originalName }
    }

    private fun fitInternalName(baseName: String, originalName: String, settings: OfflineNameSettings): String? {
        val maxLength = settings.maxNameLength.coerceIn(1, 16)
        if (baseName.length <= maxLength) {
            return baseName
        }
        return when (settings.overflowMode) {
            OfflineNameOverflowMode.KICK -> null
            OfflineNameOverflowMode.TRUNCATE -> baseName.take(maxLength)
            OfflineNameOverflowMode.HASH_SUFFIX -> hashSuffix(baseName, originalName, maxLength, settings.hashLength)
        }
    }

    private fun hashSuffix(baseName: String, originalName: String, maxLength: Int, configuredHashLength: Int): String? {
        val hashLength = configuredHashLength.coerceIn(1, 8)
        val separatorLength = 1
        val prefixLength = maxLength - hashLength - separatorLength
        if (prefixLength <= 0) {
            return null
        }
        val suffix = shortHash(originalName, hashLength)
        return baseName.take(prefixLength) + "_" + suffix
    }

    private fun shortHash(value: String, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.lowercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }.take(length)
    }
}

data class OfflineNameSettings(
    val enabled: Boolean = true,
    val prefix: String = "o_",
    val avoidDoublePrefix: Boolean = true,
    val stripDisplayPrefix: Boolean = true,
    val maxNameLength: Int = 16,
    val overflowMode: OfflineNameOverflowMode = OfflineNameOverflowMode.HASH_SUFFIX,
    val hashLength: Int = 4,
    val avoidPremiumInternalName: Boolean = true,
    val uuidSource: OfflineUuidSource = OfflineUuidSource.PREFIXED_INTERNAL_NAME
)

enum class OfflineNameOverflowMode {
    KICK,
    TRUNCATE,
    HASH_SUFFIX;

    companion object {
        fun parse(value: String?): OfflineNameOverflowMode {
            return entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: HASH_SUFFIX
        }
    }
}

enum class OfflineUuidSource {
    PREFIXED_INTERNAL_NAME,
    ORIGINAL_NAME;

    fun nameSource(originalName: String, internalName: String): String {
        return when (this) {
            PREFIXED_INTERNAL_NAME -> internalName
            ORIGINAL_NAME -> originalName
        }
    }

    companion object {
        fun parse(value: String?): OfflineUuidSource {
            return entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: PREFIXED_INTERNAL_NAME
        }
    }
}

data class OfflineNameResult(
    val success: Boolean,
    val originalName: String?,
    val internalName: String?,
    val displayName: String?,
    val uuid: UUID?,
    val failure: OfflineNameFailure?
) {
    fun identity(): PlayerIdentity {
        return PlayerIdentity(
            originalName = originalName ?: error("Missing original name"),
            internalName = internalName ?: error("Missing internal name"),
            displayName = displayName ?: error("Missing display name"),
            uuid = uuid ?: error("Missing uuid"),
            premium = false
        )
    }

    companion object {
        fun success(originalName: String, internalName: String, displayName: String, uuid: UUID): OfflineNameResult {
            return OfflineNameResult(
                success = true,
                originalName = originalName,
                internalName = internalName,
                displayName = displayName,
                uuid = uuid,
                failure = null
            )
        }

        fun failure(failure: OfflineNameFailure): OfflineNameResult {
            return OfflineNameResult(
                success = false,
                originalName = null,
                internalName = null,
                displayName = null,
                uuid = null,
                failure = failure
            )
        }
    }
}

enum class OfflineNameFailure {
    INVALID_NAME,
    NAME_TOO_LONG,
    INTERNAL_NAME_CONFLICT
}
