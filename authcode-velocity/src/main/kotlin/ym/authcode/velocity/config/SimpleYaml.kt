package ym.authcode.velocity.config

class SimpleYaml(
    content: String
) {
    private val values: Map<String, String> = parse(content)

    fun string(path: String, default: String): String {
        return values[path] ?: default
    }

    fun boolean(path: String, default: Boolean): Boolean {
        return values[path]?.lowercase()?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        } ?: default
    }

    fun long(path: String, default: Long): Long {
        return values[path]?.toLongOrNull() ?: default
    }

    private fun parse(content: String): Map<String, String> {
        val parsed = linkedMapOf<String, String>()
        val pathStack = mutableListOf<Pair<Int, String>>()
        content.lineSequence().forEach { rawLine ->
            if (rawLine.isBlank()) {
                return@forEach
            }
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("#")) {
                return@forEach
            }
            val indent = rawLine.takeWhile { it == ' ' }.length
            while (pathStack.isNotEmpty() && pathStack.last().first >= indent) {
                pathStack.removeAt(pathStack.lastIndex)
            }
            if (trimmed.endsWith(":")) {
                pathStack += indent to trimmed.removeSuffix(":").trim()
                return@forEach
            }
            val separator = trimmed.indexOf(':')
            if (separator <= 0) {
                return@forEach
            }
            val key = trimmed.substring(0, separator).trim()
            val value = unquote(trimmed.substring(separator + 1).trim())
            val prefix = pathStack.joinToString(".") { it.second }
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            parsed[path] = value
        }
        return parsed
    }

    private fun unquote(value: String): String {
        if (value.length < 2) {
            return value
        }
        val first = value.first()
        val last = value.last()
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return value
    }
}
