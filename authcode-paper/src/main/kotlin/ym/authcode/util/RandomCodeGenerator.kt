package ym.authcode.util

import java.security.SecureRandom

object RandomCodeGenerator {
    private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val DIGITS = "0123456789"
    private val random = SecureRandom()

    fun generate(length: Int = 8): String {
        return generateFrom(CHARS, length)
    }

    fun generateDigits(length: Int): String {
        return generateFrom(DIGITS, length)
    }

    fun nextLength(minLength: Int, maxLength: Int): Int {
        if (maxLength <= minLength) {
            return minLength
        }
        return minLength + random.nextInt(maxLength - minLength + 1)
    }

    private fun generateFrom(characters: String, length: Int): String {
        return buildString(length) {
            repeat(length) {
                append(characters[random.nextInt(characters.length)])
            }
        }
    }
}
