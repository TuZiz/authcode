package ym.authcode.util

import java.security.SecureRandom

object RandomCodeGenerator {
    private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()

    fun generate(length: Int = 8): String {
        return buildString(length) {
            repeat(length) {
                append(CHARS[random.nextInt(CHARS.length)])
            }
        }
    }
}
