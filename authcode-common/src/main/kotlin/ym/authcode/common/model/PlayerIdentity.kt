package ym.authcode.common.model

import java.util.Locale
import java.util.UUID

data class PlayerIdentity(
    val originalName: String,
    val internalName: String,
    val displayName: String,
    val uuid: UUID,
    val premium: Boolean
) {
    val lowerInternalName: String
        get() = internalName.lowercase(Locale.ROOT)
}
