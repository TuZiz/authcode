package ym.authcode.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

object ComponentUtil {
    private val miniMessage = MiniMessage.miniMessage()

    fun render(raw: String, prefix: String, variables: Map<String, String> = emptyMap()): Component {
        var text = raw.replace("{prefix}", prefix)
        variables.forEach { (key, value) ->
            text = text.replace("{$key}", value)
        }
        return miniMessage.deserialize(text)
    }
}
