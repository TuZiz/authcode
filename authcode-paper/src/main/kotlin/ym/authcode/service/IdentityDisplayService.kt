package ym.authcode.service

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.config.ConfigManager
import ym.authcode.config.IdentityTagStyle
import ym.authcode.message.MessageService
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.util.ComponentUtil

class IdentityDisplayService(
    private val configManager: ConfigManager,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) {
    private val plainText = PlainTextComponentSerializer.plainText()

    fun apply(player: Player, identity: PlayerIdentity) {
        val settings = configManager.current().identityDisplay
        if (!settings.enabled) {
            return
        }
        val component = identityNameComponent(identity)
        scheduler.runAtEntity(player) {
            if (settings.applyPlayerDisplayName) {
                player.displayName(component)
            }
            if (settings.applyTabList) {
                player.playerListName(component)
            }
        }
    }

    fun identityNameRaw(identity: PlayerIdentity): String {
        val settings = configManager.current().identityDisplay
        val style = style(identity)
        if (!settings.enabled || !style.enabled) {
            return effectiveDisplayName(identity)
        }
        return replaceFormatVariables(style.format, identity, style)
    }

    fun identityNameComponent(identity: PlayerIdentity): Component {
        return ComponentUtil.render(identityNameRaw(identity), "", emptyMap())
    }

    fun identityNamePlain(identity: PlayerIdentity): String {
        return plainText.serialize(identityNameComponent(identity))
    }

    fun chatSeparatorComponent(): Component {
        return messageService.render("identity.chat-separator").firstOrNull() ?: Component.text(": ")
    }

    fun identityPrefix(identity: PlayerIdentity): String {
        return identityPrefixRaw(identity)
    }

    fun identityPrefixPlain(identity: PlayerIdentity): String {
        return plainText.serialize(ComponentUtil.render(identityPrefixRaw(identity), "", emptyMap())).trim()
    }

    fun identityType(identity: PlayerIdentity): String {
        return if (identity.premium) "premium" else "offline"
    }

    fun effectiveDisplayName(identity: PlayerIdentity): String {
        if (identity.premium) {
            return identity.displayName
        }
        val settings = configManager.current()
        if (!settings.identityDisplay.stripInternalPrefix) {
            return identity.internalName
        }
        val prefix = settings.offlineName.prefix
        if (prefix.isNotEmpty() && identity.internalName.startsWith(prefix)) {
            return identity.internalName.removePrefix(prefix).ifBlank { identity.displayName }
        }
        return identity.displayName
    }

    fun variables(identity: PlayerIdentity): Map<String, String> {
        val prefix = identityPrefixRaw(identity)
        val displayName = effectiveDisplayName(identity)
        val identityName = identityNameRaw(identity)
        return mapOf(
            "name" to displayName,
            "display_name" to displayName,
            "internal_name" to identity.internalName,
            "original_name" to identity.originalName,
            "identity_prefix" to prefix,
            "identity_prefix_plain" to identityPrefixPlain(identity),
            "identity_name" to identityName,
            "identity_name_plain" to identityNamePlain(identity),
            "identity_type" to identityType(identity),
            "premium" to identity.premium.toString(),
            "uuid" to identity.uuid.toString(),
            "auth_source" to identity.authSource,
            "verified_at" to identity.verifiedAt.toString()
        )
    }

    private fun style(identity: PlayerIdentity): IdentityTagStyle {
        val settings = configManager.current().identityDisplay
        return if (identity.premium) settings.premium else settings.offline
    }

    private fun identityPrefixRaw(identity: PlayerIdentity): String {
        val style = style(identity)
        if (!style.enabled) {
            return ""
        }
        style.prefix?.let { return it }
        return if (identity.premium) {
            langFallback("display.identity.premium-prefix", "identity.premium-prefix")
        } else {
            langFallback("display.identity.offline-prefix", "identity.offline-prefix")
        }
    }

    private fun replaceFormatVariables(format: String, identity: PlayerIdentity, style: IdentityTagStyle): String {
        var text = format
        val prefix = identityPrefixRaw(identity)
        val displayName = effectiveDisplayName(identity)
        val renderName = if (format.contains("{name_color}")) {
            displayName
        } else {
            style.nameColor + displayName
        }
        val variables = mapOf(
            "prefix" to prefix,
            "name" to renderName,
            "display_name" to renderName,
            "plain_name" to displayName,
            "plain_display_name" to displayName,
            "name_color" to style.nameColor,
            "internal_name" to identity.internalName,
            "original_name" to identity.originalName,
            "identity_prefix" to prefix,
            "identity_type" to identityType(identity),
            "premium" to identity.premium.toString(),
            "uuid" to identity.uuid.toString(),
            "auth_source" to identity.authSource,
            "verified_at" to identity.verifiedAt.toString()
        )
        variables.forEach { (key, value) ->
            text = text.replace("{$key}", value)
        }
        return text
    }

    private fun langFallback(primary: String, fallback: String): String {
        val primaryText = messageService.plain(primary)
        if (!primaryText.startsWith("<red>Missing language key:")) {
            return primaryText
        }
        return messageService.plain(fallback)
    }
}
