package ym.authcode.velocity.lang

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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

    fun render(key: String): Component {
        val prefix = yaml.string("prefix", "")
        val text = yaml.string(key, key).replace("{prefix}", prefix)
        return miniMessage.deserialize(text)
    }
}
