package ym.authcode.service

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.config.ConfigManager
import ym.authcode.message.MessageService
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.util.ComponentUtil

class IdentityDisplayService(
    private val configManager: ConfigManager,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) {
    fun apply(player: Player, identity: PlayerIdentity) {
        val settings = configManager.current().identityDisplay
        if (!settings.enabled) {
            return
        }
        val component = identityNameComponent(identity)
        scheduler.runAtEntity(player) {
            if (settings.applyDisplayName) {
                player.displayName(component)
            }
            if (settings.applyTab) {
                player.playerListName(component)
            }
        }
    }

    fun identityNameRaw(identity: PlayerIdentity): String {
        val settings = configManager.current().identityDisplay
        val format = if (identity.premium) settings.premiumFormat else settings.offlineFormat
        return replaceVariables(format, identity)
    }

    fun identityNameComponent(identity: PlayerIdentity): Component {
        return ComponentUtil.render(identityNameRaw(identity), "", variables(identity))
    }

    fun chatSeparatorComponent(): Component {
        return messageService.render("identity.chat-separator").firstOrNull() ?: Component.text(": ")
    }

    fun identityPrefix(identity: PlayerIdentity): String {
        return messageService.plain(if (identity.premium) "identity.premium-prefix" else "identity.offline-prefix")
    }

    fun variables(identity: PlayerIdentity): Map<String, String> {
        val prefix = identityPrefix(identity)
        val identityName = replaceVariables(
            if (identity.premium) {
                configManager.current().identityDisplay.premiumFormat
            } else {
                configManager.current().identityDisplay.offlineFormat
            },
            identity,
            prefix
        )
        return mapOf(
            "name" to identity.displayName,
            "display_name" to identity.displayName,
            "internal_name" to identity.internalName,
            "original_name" to identity.originalName,
            "identity_prefix" to prefix,
            "identity_name" to identityName,
            "premium" to identity.premium.toString(),
            "uuid" to identity.uuid.toString()
        )
    }

    private fun replaceVariables(format: String, identity: PlayerIdentity, prefix: String = identityPrefix(identity)): String {
        var text = format
        val variables = mapOf(
            "name" to identity.displayName,
            "display_name" to identity.displayName,
            "internal_name" to identity.internalName,
            "original_name" to identity.originalName,
            "identity_prefix" to prefix,
            "premium" to identity.premium.toString(),
            "uuid" to identity.uuid.toString()
        )
        variables.forEach { (key, value) ->
            text = text.replace("{$key}", value)
        }
        return text
    }
}
