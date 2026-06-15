package ym.authcode.storage.sqlite

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
                statement.executeQuery().use { result ->
                    return if (result.next()) mapPlayer(result) else null
                }
            }
        }
    }

    fun saveRegistered(
        uuid: UUID,
        name: String,
        lowerName: String,
        passwordHash: String,
        invitedByCode: String,
        ip: String,
        now: Long
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.SAVE_REGISTERED_PLAYER).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, name)
                statement.setString(3, lowerName)
                statement.setString(4, passwordHash)
                statement.setString(5, invitedByCode)
                statement.setString(6, ip)
                statement.setString(7, ip)
                statement.setLong(8, now)
                statement.setLong(9, now)
                statement.setLong(10, now)
                statement.setLong(11, now)
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
                statement.executeUpdate()
            }
        }
    }

    fun setPremiumOverride(name: String, lowerName: String, premium: Boolean, now: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.SET_PREMIUM_OVERRIDE).use { statement ->
                statement.setString(1, name)
                statement.setString(2, lowerName)
                statement.setInt(3, if (premium) 1 else 0)
                statement.setLong(4, now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
        }
    }

    fun updateProxyAuthStatus(
        uuid: UUID,
        name: String,
        lowerName: String,
        premium: Boolean,
        authSource: String,
        now: Long
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.UPDATE_PROXY_AUTH_STATUS).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, name)
                statement.setString(3, lowerName)
                statement.setString(4, authSource)
                statement.setInt(5, if (premium) 1 else 0)
                statement.setLong(6, now)
                statement.setLong(7, now)
                statement.setLong(8, now)
                statement.executeUpdate()
            }
        }
    }

    fun recordProxyAuthLog(log: ProxyAuthLog) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.INSERT_PROXY_AUTH_LOG).use { statement ->
                statement.setString(1, log.uuid.toString())
                statement.setString(2, log.name)
                statement.setInt(3, if (log.premium) 1 else 0)
                statement.setString(4, log.authSource)
                statement.setString(5, log.remoteIp)
                statement.setString(6, log.serverName)
                statement.setString(7, log.nonce)
                statement.setLong(8, log.verifyTime)
                statement.setLong(9, log.createdAt)
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
            name = result.getString("name"),
            lowerName = result.getString("lower_name"),
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
