package ym.authcode.velocity.config

class SimpleYaml(
    content: String
) {
    private val parsed: ParsedYaml = parse(content)
    private val values: Map<String, String> = parsed.values
    private val lists: Map<String, List<String>> = parsed.lists

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

    fun stringList(path: String, default: List<String> = emptyList()): List<String> {
        lists[path]?.let { return it }
        return values[path]
            ?.takeIf { it.startsWith("[") && it.endsWith("]") }
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.split(",")
            ?.map { unquote(it.trim()) }
            ?.filter { it.isNotBlank() }
            ?: default
    }

    private fun parse(content: String): ParsedYaml {
        val parsed = linkedMapOf<String, String>()
        val parsedLists = linkedMapOf<String, MutableList<String>>()
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
            if (trimmed.startsWith("- ")) {
                val prefix = pathStack.joinToString(".") { it.second }
                if (prefix.isNotEmpty()) {
                    parsedLists.getOrPut(prefix) { mutableListOf() } += unquote(trimmed.removePrefix("-").trim())
                }
                return@forEach
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
        return ParsedYaml(parsed, parsedLists)
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

    private data class ParsedYaml(
        val values: Map<String, String>,
        val lists: Map<String, List<String>>
    )
}
