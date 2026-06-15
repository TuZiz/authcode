package ym.authcode.velocity.config

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class VelocityConfigLoader(
    private val dataDirectory: Path,
    private val classLoader: ClassLoader
) {
    fun load(): VelocitySettings {
        val configPath = ensureResource("config.yml")
        val content = Files.readString(configPath, StandardCharsets.UTF_8)
        return VelocitySettings.from(SimpleYaml(content))
    }

    fun ensureResource(resourcePath: String): Path {
        val target = dataDirectory.resolve(resourcePath)
        if (Files.notExists(target)) {
            Files.createDirectories(target.parent ?: dataDirectory)
            classLoader.getResourceAsStream(resourcePath).use { input ->
                requireNotNull(input) { "Missing resource $resourcePath" }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return target
    }
}
