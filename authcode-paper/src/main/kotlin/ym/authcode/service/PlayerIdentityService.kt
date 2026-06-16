package ym.authcode.service

import ym.authcode.common.model.PlayerIdentity
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerIdentityService {
    private val identitiesByUuid = ConcurrentHashMap<UUID, PlayerIdentity>()
    private val uuidByLowerInternalName = ConcurrentHashMap<String, UUID>()

    fun remember(identity: PlayerIdentity) {
        identitiesByUuid.put(identity.uuid, identity)?.let { previous ->
            uuidByLowerInternalName.remove(previous.lowerInternalName)
        }
        uuidByLowerInternalName[identity.lowerInternalName] = identity.uuid
    }

    fun find(uuid: UUID): PlayerIdentity? {
        return identitiesByUuid[uuid]
    }

    fun findByName(name: String): PlayerIdentity? {
        val uuid = uuidByLowerInternalName[name.lowercase(Locale.ROOT)] ?: return null
        return identitiesByUuid[uuid]
    }

    fun fallback(snapshot: PlayerSnapshot, premium: Boolean = false): PlayerIdentity {
        return PlayerIdentity(
            originalName = snapshot.name,
            internalName = snapshot.name,
            displayName = snapshot.name,
            uuid = snapshot.uuid,
            premium = premium,
            verifiedAt = System.currentTimeMillis(),
            authSource = "LOCAL"
        )
    }

    fun remove(uuid: UUID) {
        identitiesByUuid.remove(uuid)?.let { identity ->
            uuidByLowerInternalName.remove(identity.lowerInternalName)
        }
    }

    fun clear() {
        identitiesByUuid.clear()
        uuidByLowerInternalName.clear()
    }
}
