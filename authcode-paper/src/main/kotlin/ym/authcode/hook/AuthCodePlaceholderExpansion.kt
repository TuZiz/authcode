package ym.authcode.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.PlayerIdentityService
import java.util.Locale

class AuthCodePlaceholderExpansion(
    private val plugin: JavaPlugin,
    private val identityService: PlayerIdentityService,
    private val identityDisplayService: IdentityDisplayService
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "authcode"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ").ifBlank { "AuthCode" }

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String {
        if (player == null) {
            return ""
        }
        val identity = identityService.find(player.uniqueId) ?: return ""
        return when (params.lowercase(Locale.ROOT)) {
            "identity_prefix" -> identityDisplayService.identityPrefixPlain(identity)
            "identity_type" -> identityDisplayService.identityType(identity)
            "display_name" -> identityDisplayService.effectiveDisplayName(identity)
            "internal_name" -> identity.internalName
            "original_name" -> identity.originalName
            "premium" -> identity.premium.toString()
            "identity_name" -> identityDisplayService.identityNamePlain(identity)
            "auth_source" -> identity.authSource
            else -> ""
        }
    }
}
