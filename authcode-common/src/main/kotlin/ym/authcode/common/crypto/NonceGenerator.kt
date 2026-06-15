package ym.authcode.common.crypto

import java.security.SecureRandom
import java.util.Base64

object NonceGenerator {
    private val random = SecureRandom()

    fun generate(lengthBytes: Int = 24): String {
        val bytes = ByteArray(lengthBytes.coerceAtLeast(16))
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
