package ym.authcode.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeParser {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun parseExpireTime(input: String?, now: Long): Long? {
        if (input.isNullOrBlank() || input.equals("never", ignoreCase = true)) {
            return null
        }
        val normalized = input.trim().lowercase(Locale.ROOT)
        val amount = normalized.dropLast(1).toLongOrNull() ?: throw IllegalArgumentException()
        val unit = normalized.last()
        val millis = when (unit) {
            'm' -> amount * 60_000L
            'h' -> amount * 3_600_000L
            'd' -> amount * 86_400_000L
            else -> throw IllegalArgumentException()
        }
        if (amount <= 0 || millis <= 0) {
            throw IllegalArgumentException()
        }
        return now + millis
    }

    fun format(epochMillis: Long?, neverText: String): String {
        return epochMillis?.let { formatter.format(Instant.ofEpochMilli(it)) } ?: neverText
    }
}
