package ym.authcode.storage.sqlite

import ym.authcode.model.InviteCode
import ym.authcode.model.InviteCodeUse
import ym.authcode.model.InviteUseResult
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class SQLiteInviteCodes(
    private val dataSource: DataSource
) {
    fun create(code: InviteCode): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.CREATE_INVITE_CODE).use { statement ->
                statement.setString(1, code.code)
                statement.setString(2, code.lowerCode)
                statement.setInt(3, code.maxUses)
                statement.setInt(4, code.usedCount)
                statement.setString(5, code.createdBy)
                statement.setLong(6, code.createdTime)
                if (code.expireTime == null) {
                    statement.setNull(7, Types.BIGINT)
                } else {
                    statement.setLong(7, code.expireTime)
                }
                statement.setInt(8, if (code.enabled) 1 else 0)
                statement.setLong(9, code.createdAt)
                statement.setLong(10, code.updatedAt)
                return statement.executeUpdate() > 0
            }
        }
    }

    fun delete(lowerCode: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.DELETE_INVITE_CODE).use { statement ->
                statement.setString(1, lowerCode)
                return statement.executeUpdate() > 0
            }
        }
    }

    fun find(lowerCode: String): InviteCode? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.FIND_INVITE_CODE).use { statement ->
                statement.setString(1, lowerCode)
                statement.executeQuery().use { result ->
                    return if (result.next()) mapCode(result) else null
                }
            }
        }
    }

    fun list(): List<InviteCode> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.LIST_INVITE_CODES).use { statement ->
                statement.executeQuery().use { result ->
                    val codes = mutableListOf<InviteCode>()
                    while (result.next()) {
                        codes.add(mapCode(result))
                    }
                    return codes
                }
            }
        }
    }

    fun listUses(lowerCode: String): List<InviteCodeUse> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SQLiteQueries.LIST_INVITE_CODE_USES).use { statement ->
                statement.setString(1, lowerCode)
                statement.executeQuery().use { result ->
                    val uses = mutableListOf<InviteCodeUse>()
                    while (result.next()) {
                        uses.add(mapUse(result))
                    }
                    return uses
                }
            }
        }
    }

    fun use(codeInput: String, playerUuid: UUID, playerName: String, ip: String, now: Long): InviteUseResult {
        val lowerCode = codeInput.lowercase()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val code = selectForUse(connection, lowerCode) ?: return rollback(connection, InviteUseResult.NOT_FOUND)
                if (!code.enabled) {
                    return rollback(connection, InviteUseResult.DISABLED)
                }
                if (code.isExpired(now)) {
                    return rollback(connection, InviteUseResult.EXPIRED)
                }
                if (code.remainingUses() <= 0) {
                    return rollback(connection, InviteUseResult.USED_UP)
                }
                incrementUseCount(connection, lowerCode, now)
                insertUse(connection, code.code, lowerCode, playerUuid, playerName, ip, now)
                connection.commit()
                return InviteUseResult.SUCCESS
            } catch (throwable: Throwable) {
                connection.rollback()
                throw throwable
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun selectForUse(connection: Connection, lowerCode: String): InviteCode? {
        connection.prepareStatement(SQLiteQueries.FIND_INVITE_CODE).use { statement ->
            statement.setString(1, lowerCode)
            statement.executeQuery().use { result ->
                return if (result.next()) mapCode(result) else null
            }
        }
    }

    private fun incrementUseCount(connection: Connection, lowerCode: String, now: Long) {
        connection.prepareStatement(SQLiteQueries.UPDATE_INVITE_USED).use { statement ->
            statement.setLong(1, now)
            statement.setString(2, lowerCode)
            if (statement.executeUpdate() <= 0) {
                throw IllegalStateException("Invite code use count update failed")
            }
        }
    }

    private fun insertUse(
        connection: Connection,
        code: String,
        lowerCode: String,
        playerUuid: UUID,
        playerName: String,
        ip: String,
        now: Long
    ) {
        connection.prepareStatement(SQLiteQueries.INSERT_INVITE_USE).use { statement ->
            statement.setString(1, code)
            statement.setString(2, lowerCode)
            statement.setString(3, playerUuid.toString())
            statement.setString(4, playerName)
            statement.setString(5, ip)
            statement.setLong(6, now)
            statement.executeUpdate()
        }
    }

    private fun <T> rollback(connection: Connection, result: T): T {
        connection.rollback()
        return result
    }

    private fun mapCode(result: ResultSet): InviteCode {
        val expireTime = result.getObject("expire_time")?.let { result.getLong("expire_time") }
        return InviteCode(
            code = result.getString("code"),
            lowerCode = result.getString("lower_code"),
            maxUses = result.getInt("max_uses"),
            usedCount = result.getInt("used_count"),
            createdBy = result.getString("created_by"),
            createdTime = result.getLong("created_time"),
            expireTime = expireTime,
            enabled = result.getInt("enabled") == 1,
            createdAt = result.getLong("created_at"),
            updatedAt = result.getLong("updated_at")
        )
    }

    private fun mapUse(result: ResultSet): InviteCodeUse {
        return InviteCodeUse(
            code = result.getString("code"),
            lowerCode = result.getString("lower_code"),
            playerUuid = UUID.fromString(result.getString("player_uuid")),
            playerName = result.getString("player_name"),
            ip = result.getString("ip"),
            useTime = result.getLong("use_time")
        )
    }
}
