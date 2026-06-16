package ym.authcode.storage.sqlite

import ym.authcode.common.model.PlayerIdentity
import ym.authcode.model.PlayerAuthData
import ym.authcode.model.ProxyAuthLog
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class SQLitePlayers(
    private val dataSource: DataSource
) {
    fun findByLowerName(lowerName: String): PlayerAuthData? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.FIND_PLAYER_BY_LOWER_NAME).use { statement ->
                statement.setString(1, lowerName)
                statement.setString(2, lowerName)
                statement.executeQuery().use { result ->
                    return if (result.next()) mapPlayer(result) else null
                }
            }
        }
    }

    fun saveRegistered(
        identity: PlayerIdentity,
        passwordHash: String,
        invitedByCode: String,
        ip: String,
        now: Long
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.SAVE_REGISTERED_PLAYER).use { statement ->
                statement.setString(1, identity.uuid.toString())
                statement.setString(2, identity.internalName)
                statement.setString(3, identity.lowerInternalName)
                statement.setString(4, identity.originalName)
                statement.setString(5, identity.internalName)
                statement.setString(6, identity.lowerInternalName)
                statement.setString(7, identity.displayName)
                statement.setString(8, passwordHash)
                statement.setString(9, invitedByCode)
                statement.setString(10, ip)
                statement.setString(11, ip)
                statement.setLong(12, now)
                statement.setLong(13, now)
                statement.setLong(14, now)
                statement.setLong(15, now)
                statement.executeUpdate()
            }
        }
    }

    fun updateLogin(lowerName: String, ip: String, now: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.UPDATE_LOGIN).use { statement ->
                statement.setString(1, ip)
                statement.setLong(2, now)
                statement.setLong(3, now)
                statement.setString(4, lowerName)
                statement.setString(5, lowerName)
                statement.executeUpdate()
            }
        }
    }

    fun updatePassword(lowerName: String, passwordHash: String, now: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.UPDATE_PASSWORD).use { statement ->
                statement.setString(1, passwordHash)
                statement.setLong(2, now)
                statement.setString(3, lowerName)
                statement.setString(4, lowerName)
                statement.executeUpdate()
            }
        }
    }

    fun setPremiumOverride(name: String, lowerName: String, premium: Boolean, now: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.SET_PREMIUM_OVERRIDE).use { statement ->
                statement.setString(1, name)
                statement.setString(2, lowerName)
                statement.setString(3, name)
                statement.setString(4, name)
                statement.setString(5, lowerName)
                statement.setString(6, name)
                statement.setInt(7, if (premium) 1 else 0)
                statement.setLong(8, now)
                statement.setLong(9, now)
                statement.executeUpdate()
            }
        }
    }

    fun updateProxyAuthStatus(
        identity: PlayerIdentity,
        authSource: String,
        now: Long
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.UPDATE_PROXY_AUTH_STATUS).use { statement ->
                statement.setString(1, identity.uuid.toString())
                statement.setString(2, identity.internalName)
                statement.setString(3, identity.lowerInternalName)
                statement.setString(4, identity.originalName)
                statement.setString(5, identity.internalName)
                statement.setString(6, identity.lowerInternalName)
                statement.setString(7, identity.displayName)
                statement.setString(8, authSource)
                statement.setInt(9, if (identity.premium) 1 else 0)
                statement.setLong(10, now)
                statement.setLong(11, now)
                statement.setLong(12, now)
                statement.executeUpdate()
            }
        }
    }

    fun recordProxyAuthLog(log: ProxyAuthLog) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.INSERT_PROXY_AUTH_LOG).use { statement ->
                statement.setString(1, log.uuid.toString())
                statement.setString(2, log.internalName)
                statement.setString(3, log.originalName)
                statement.setString(4, log.internalName)
                statement.setString(5, log.displayName)
                statement.setInt(6, if (log.premium) 1 else 0)
                statement.setString(7, log.authSource)
                statement.setString(8, log.remoteIp)
                statement.setString(9, log.serverName)
                statement.setString(10, log.nonce)
                statement.setLong(11, log.verifyTime)
                statement.setLong(12, log.createdAt)
                statement.executeUpdate()
            }
        }
    }

    private fun mapPlayer(result: ResultSet): PlayerAuthData {
        val uuidText = result.getString("uuid")
        val premiumValue = result.getObject("premium")
        val lastProxyPremiumValue = result.getObject("last_proxy_premium")
        return PlayerAuthData(
            uuid = uuidText?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            originalName = result.getString("original_name") ?: result.getString("name"),
            internalName = result.getString("internal_name") ?: result.getString("name"),
            lowerInternalName = result.getString("lower_internal_name") ?: result.getString("lower_name"),
            displayName = result.getString("display_name") ?: result.getString("name"),
            passwordHash = result.getString("password_hash"),
            premium = premiumValue?.let { result.getInt("premium") == 1 },
            registered = result.getInt("registered") == 1,
            invitedByCode = result.getString("invited_by_code"),
            registerIp = result.getString("register_ip"),
            lastIp = result.getString("last_ip"),
            authSource = result.getString("auth_source"),
            lastProxyPremium = lastProxyPremiumValue?.let { result.getInt("last_proxy_premium") == 1 },
            lastProxyVerifyTime = result.getLong("last_proxy_verify_time"),
            registerTime = result.getLong("register_time"),
            lastLoginTime = result.getLong("last_login_time"),
            createdAt = result.getLong("created_at"),
            updatedAt = result.getLong("updated_at")
        )
    }
}
