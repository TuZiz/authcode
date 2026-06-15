package ym.authcode.common.protocol

import ym.authcode.common.model.ProxyAuthPayload
import java.nio.charset.StandardCharsets

object AuthCodePayloadCodec {
    fun encode(payload: ProxyAuthPayload): ByteArray {
        return toJson(payload).toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): ProxyAuthPayload {
        return fromJson(String(bytes, StandardCharsets.UTF_8))
    }

    fun toJson(payload: ProxyAuthPayload): String {
        return buildString {
            append('{')
            append("\"version\":").append(payload.version).append(',')
            append("\"username\":").append(jsonString(payload.username)).append(',')
            append("\"uuid\":").append(jsonString(payload.uuid)).append(',')
            append("\"premium\":").append(payload.premium).append(',')
            append("\"timestamp\":").append(payload.timestamp).append(',')
            append("\"nonce\":").append(jsonString(payload.nonce)).append(',')
            append("\"signature\":").append(jsonString(payload.signature))
            append('}')
        }
    }

    fun fromJson(json: String): ProxyAuthPayload {
        val values = JsonObjectParser(json).parse()
        return ProxyAuthPayload(
            version = values.required("version").toIntOrNull() ?: error("Invalid version"),
            username = values.required("username"),
            uuid = values.required("uuid"),
            premium = values.required("premium").toBooleanStrictOrNull() ?: error("Invalid premium"),
            timestamp = values.required("timestamp").toLongOrNull() ?: error("Invalid timestamp"),
            nonce = values.required("nonce"),
            signature = values.required("signature")
        )
    }

    private fun Map<String, String>.required(key: String): String {
        return this[key] ?: error("Missing payload field: $key")
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u").append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }
}

private class JsonObjectParser(
    private val source: String
) {
    private var index = 0

    fun parse(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        skipWhitespace()
        expect('{')
        skipWhitespace()
        if (peek() == '}') {
            index++
            return values
        }
        while (index < source.length) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            values[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index++
                    skipWhitespace()
                }
                '}' -> {
                    index++
                    skipWhitespace()
                    if (index != source.length) {
                        error("Trailing payload data")
                    }
                    return values
                }
                else -> error("Expected comma or object end")
            }
        }
        error("Unclosed JSON object")
    }

    private fun parseValue(): String {
        return when (peek()) {
            '"' -> parseString()
            't' -> parseLiteral("true")
            'f' -> parseLiteral("false")
            else -> parseNumber()
        }
    }

    private fun parseLiteral(literal: String): String {
        if (!source.regionMatches(index, literal, 0, literal.length)) {
            error("Invalid literal")
        }
        index += literal.length
        return literal
    }

    private fun parseNumber(): String {
        val start = index
        if (peek() == '-') {
            index++
        }
        while (index < source.length && source[index].isDigit()) {
            index++
        }
        if (start == index || (source[start] == '-' && start + 1 == index)) {
            error("Invalid number")
        }
        return source.substring(start, index)
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < source.length) {
            val char = source[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(char)
            }
        }
        error("Unclosed string")
    }

    private fun parseEscape(): Char {
        val escaped = source.getOrNull(index++) ?: error("Invalid escape")
        return when (escaped) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> error("Invalid escape")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > source.length) {
            error("Invalid unicode escape")
        }
        val value = source.substring(index, index + 4).toInt(16)
        index += 4
        return value.toChar()
    }

    private fun expect(char: Char) {
        if (peek() != char) {
            error("Expected $char")
        }
        index++
    }

    private fun peek(): Char {
        return source.getOrNull(index) ?: error("Unexpected end of payload")
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }
}
