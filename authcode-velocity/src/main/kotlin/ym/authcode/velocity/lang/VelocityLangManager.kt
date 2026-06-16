package ym.authcode.velocity.lang

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ym.authcode.velocity.config.SimpleYaml
import ym.authcode.velocity.config.VelocityConfigLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class VelocityLangManager(
    private val configLoader: VelocityConfigLoader
) {
    private val miniMessage = MiniMessage.miniMessage()
    private var yaml = SimpleYaml("")

    fun load() {
        val path = configLoader.ensureResource("lang/zh_cn.yml")
        yaml = SimpleYaml(Files.readString(path, StandardCharsets.UTF_8))
    }

    fun render(key: String, variables: Map<String, String> = emptyMap()): Component {
        return miniMessage.deserialize(resolve(key, variables))
    }

    fun plain(key: String, variables: Map<String, String> = emptyMap()): String {
        return PlainTextComponentSerializer.plainText().serialize(render(key, variables))
    }

    private fun resolve(key: String, variables: Map<String, String>): String {
        val prefix = yaml.string("prefix", "")
        var text = yaml.string(key, key).replace("{prefix}", prefix)
        variables.forEach { (variable, value) ->
            text = text.replace("{$variable}", value)
        }
        return text
    }
}
