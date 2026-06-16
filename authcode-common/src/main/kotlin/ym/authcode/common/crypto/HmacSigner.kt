package ym.authcode.common.crypto

import ym.authcode.common.model.ProxyAuthPayload
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSigner {
    private const val ALGORITHM = "HmacSHA256"

    fun canonicalString(
        version: Int,
        username: String,
        uuid: String,
        originalName: String,
        internalName: String,
        displayName: String,
        premium: Boolean,
        authType: String,
        timestamp: Long,
        nonce: String
    ): String {
        return "$version|$username|$uuid|$originalName|$internalName|$displayName|${premium.toString().lowercase()}|$authType|$timestamp|$nonce"
    }

    fun canonicalString(payload: ProxyAuthPayload): String {
        return canonicalString(
            payload.version,
            payload.username,
            payload.uuid,
            payload.originalName,
            payload.internalName,
            payload.displayName,
            payload.premium,
            payload.authType,
            payload.timestamp,
            payload.nonce
        )
    }

    fun sign(
        secret: String,
        version: Int,
        username: String,
        uuid: String,
        originalName: String,
        internalName: String,
        displayName: String,
        premium: Boolean,
        authType: String,
        timestamp: Long,
        nonce: String
    ): String {
        return sign(
            secret,
            canonicalString(version, username, uuid, originalName, internalName, displayName, premium, authType, timestamp, nonce)
        )
    }

    fun sign(secret: String, canonicalPayload: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        return mac.doFinal(canonicalPayload.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    fun signPayload(secret: String, payload: ProxyAuthPayload): ProxyAuthPayload {
        val signature = sign(secret, canonicalString(payload))
        return payload.copy(signature = signature)
    }

    fun verify(secret: String, payload: ProxyAuthPayload): Boolean {
        val expected = sign(secret, canonicalString(payload)).hexToBytesOrNull() ?: return false
        val actual = payload.signature.hexToBytesOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        val normalized = trim().lowercase()
        if (normalized.length % 2 != 0) {
            return null
        }
        return runCatching {
            ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
