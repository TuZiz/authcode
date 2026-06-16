package ym.authcode.velocity.storage

import org.slf4j.Logger
import org.sqlite.JDBC
import ym.authcode.velocity.config.VelocitySettings
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VelocityAuthStorage(
    private val dataDirectory: Path,
    private val settingsProvider: () -> VelocitySettings,
    private val logger: Logger
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AuthCode-Velocity-SQLite").apply { isDaemon = true }
    }

    fun initialize(): CompletableFuture<Void> {
        return runAsync {
            Files.createDirectories(dataDirectory)
            Class.forName(JDBC::class.java.name)
            connection().use { connection ->
                schemaStatements.forEach { sql ->
                    connection.prepareStatement(sql).use { it.executeUpdate() }
                }
                migrateAuthProfileDualTypeConstraint(connection)
            }
        }
    }

    fun findPremiumProfileByOriginalLower(originalNameLower: String): CompletableFuture<AuthProfile?> {
        val lowerName = originalNameLower.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                findProfileByOriginalLowerAndType(connection, lowerName, AuthProfileType.PREMIUM)
            }
        }
    }

    fun findOfflineProfileByOriginalLower(originalNameLower: String): CompletableFuture<AuthProfile?> {
        val lowerName = originalNameLower.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                findProfileByOriginalLowerAndType(connection, lowerName, AuthProfileType.OFFLINE)
            }
        }
    }

    fun findProfilesByOriginalLower(originalNameLower: String): CompletableFuture<List<AuthProfile>> {
        val lowerName = originalNameLower.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                findProfilesByOriginalLower(connection, lowerName)
            }
        }
    }

    fun findProfileByInternalName(name: String): CompletableFuture<AuthProfile?> {
        val lowerName = name.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                findProfileByInternalName(connection, lowerName)
            }
        }
    }

    fun findProfileByName(name: String): CompletableFuture<AuthProfile?> {
        val lowerName = name.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                findProfileByInternalName(connection, lowerName) ?: findPreferredProfileByOriginalLower(connection, lowerName)
            }
        }
    }

    fun findValidPending(
        usernameLower: String,
        ip: String,
        matchIp: Boolean,
        now: Long
    ): CompletableFuture<PendingOfflineRename?> {
        return supplyAsync {
            connection().use { connection ->
                deleteExpiredPending(connection, now)
                val sql = if (matchIp) {
                    """
                    SELECT id, username, username_lower, offline_name, ip, expires_at, created_at
                    FROM pending_offline_rename
                    WHERE username_lower = ? AND ip = ? AND expires_at > ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """.trimIndent()
                } else {
                    """
                    SELECT id, username, username_lower, offline_name, ip, expires_at, created_at
                    FROM pending_offline_rename
                    WHERE username_lower = ? AND expires_at > ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """.trimIndent()
                }
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, usernameLower)
                    if (matchIp) {
                        statement.setString(2, ip)
                        statement.setLong(3, now)
                    } else {
                        statement.setLong(2, now)
                    }
                    statement.executeQuery().use { result ->
                        if (result.next()) mapPending(result) else null
                    }
                }
            }
        }
    }

    fun createPending(
        username: String,
        offlineName: String,
        ip: String,
        expiresAt: Long,
        now: Long
    ): CompletableFuture<Void> {
        val usernameLower = username.lowercase(Locale.ROOT)
        return runAsync {
            connection().use { connection ->
                deleteExpiredPending(connection, now)
                connection.prepareStatement(
                    """
                    DELETE FROM pending_offline_rename
                    WHERE username_lower = ? AND ip = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, usernameLower)
                    statement.setString(2, ip)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO pending_offline_rename (
                        username, username_lower, offline_name, ip, expires_at, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, usernameLower)
                    statement.setString(3, offlineName)
                    statement.setString(4, ip)
                    statement.setLong(5, expiresAt)
                    statement.setLong(6, now)
                    statement.executeUpdate()
                }
            }
        }
    }

    fun upsertOfflineProfile(
        originalName: String,
        internalName: String,
        displayName: String,
        uuid: UUID,
        now: Long
    ): CompletableFuture<AuthProfile> {
        return supplyAsync {
            connection().use { connection ->
                upsertOfflineProfile(connection, originalName, internalName, displayName, uuid, now)
                findProfileByOriginalLowerAndType(
                    connection,
                    originalName.lowercase(Locale.ROOT),
                    AuthProfileType.OFFLINE
                )
                    ?: error("Offline profile was not saved")
            }
        }
    }

    fun confirmPendingAsOfflineProfile(
        pending: PendingOfflineRename,
        uuid: UUID,
        now: Long
    ): CompletableFuture<AuthProfile> {
        return supplyAsync {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    upsertOfflineProfile(connection, pending.username, pending.offlineName, pending.username, uuid, now)
                    deletePending(connection, pending.id)
                    val profile = findProfileByOriginalLowerAndType(
                        connection,
                        pending.usernameLower,
                        AuthProfileType.OFFLINE
                    )
                        ?: error("Offline pending profile was not saved")
                    connection.commit()
                    profile
                } catch (throwable: Throwable) {
                    connection.rollback()
                    throw throwable
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    fun bindPremiumProfile(name: String, uuid: UUID, now: Long): CompletableFuture<AuthProfile> {
        val lowerName = name.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    findProfileByOriginalLowerAndType(connection, lowerName, AuthProfileType.PREMIUM)?.let { old ->
                        recordMigration(connection, old, AuthProfileType.PREMIUM, "PREMIUM_BIND", now)
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO auth_profile (
                            original_name, original_name_lower, internal_name, display_name,
                            uuid, auth_type, premium_bound, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, 'PREMIUM', 1, ?, ?)
                        ON CONFLICT(original_name_lower, auth_type) DO UPDATE SET
                            original_name = excluded.original_name,
                            internal_name = excluded.internal_name,
                            display_name = excluded.display_name,
                            uuid = excluded.uuid,
                            premium_bound = 1,
                            updated_at = excluded.updated_at
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, name)
                        statement.setString(2, lowerName)
                        statement.setString(3, name)
                        statement.setString(4, name)
                        statement.setString(5, uuid.toString())
                        statement.setLong(6, now)
                        statement.setLong(7, now)
                        statement.executeUpdate()
                    }
                    val profile = findProfileByOriginalLowerAndType(connection, lowerName, AuthProfileType.PREMIUM)
                        ?: error("Premium profile was not saved")
                    connection.commit()
                    profile
                } catch (throwable: Throwable) {
                    connection.rollback()
                    throw throwable
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    fun unbindPremiumProfile(name: String, now: Long): CompletableFuture<Boolean> {
        val lowerName = name.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    val old = findProfileByOriginalLowerAndType(connection, lowerName, AuthProfileType.PREMIUM) ?: run {
                        connection.commit()
                        return@supplyAsync false
                    }
                    recordMigration(connection, old, AuthProfileType.OFFLINE, "PREMIUM_UNBIND_DELETE", now)
                    val deleted = connection.prepareStatement(
                        "DELETE FROM auth_profile WHERE original_name_lower = ? AND auth_type = 'PREMIUM'"
                    ).use { statement ->
                        statement.setString(1, lowerName)
                        statement.executeUpdate()
                    } > 0
                    connection.commit()
                    deleted
                } catch (throwable: Throwable) {
                    connection.rollback()
                    throw throwable
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    fun listPending(now: Long): CompletableFuture<List<PendingOfflineRename>> {
        return supplyAsync {
            connection().use { connection ->
                deleteExpiredPending(connection, now)
                connection.prepareStatement(
                    """
                    SELECT id, username, username_lower, offline_name, ip, expires_at, created_at
                    FROM pending_offline_rename
                    ORDER BY created_at DESC
                    LIMIT 100
                    """.trimIndent()
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        val values = mutableListOf<PendingOfflineRename>()
                        while (result.next()) {
                            values += mapPending(result)
                        }
                        values
                    }
                }
            }
        }
    }

    fun clearPending(username: String): CompletableFuture<Int> {
        val usernameLower = username.lowercase(Locale.ROOT)
        return supplyAsync {
            connection().use { connection ->
                connection.prepareStatement("DELETE FROM pending_offline_rename WHERE username_lower = ?").use { statement ->
                    statement.setString(1, usernameLower)
                    statement.executeUpdate()
                }
            }
        }
    }

    fun cleanupPending(now: Long): CompletableFuture<Int> {
        return supplyAsync {
            connection().use { connection ->
                deleteExpiredPending(connection, now)
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun upsertOfflineProfile(
        connection: Connection,
        originalName: String,
        internalName: String,
        displayName: String,
        uuid: UUID,
        now: Long
    ) {
        connection.prepareStatement(
            """
            INSERT INTO auth_profile (
                original_name, original_name_lower, internal_name, display_name,
                uuid, auth_type, premium_bound, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, 'OFFLINE', 0, ?, ?)
            ON CONFLICT(original_name_lower, auth_type) DO UPDATE SET
                original_name = excluded.original_name,
                internal_name = excluded.internal_name,
                display_name = excluded.display_name,
                uuid = excluded.uuid,
                premium_bound = 0,
                updated_at = excluded.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, originalName)
            statement.setString(2, originalName.lowercase(Locale.ROOT))
            statement.setString(3, internalName)
            statement.setString(4, displayName)
            statement.setString(5, uuid.toString())
            statement.setLong(6, now)
            statement.setLong(7, now)
            statement.executeUpdate()
        }
    }

    private fun findPreferredProfileByOriginalLower(connection: Connection, originalNameLower: String): AuthProfile? {
        return findProfilesByOriginalLower(connection, originalNameLower)
            .sortedWith(compareBy<AuthProfile> { if (it.authType == AuthProfileType.PREMIUM) 0 else 1 }
                .thenByDescending { it.premiumBound }
                .thenByDescending { it.updatedAt })
            .firstOrNull()
    }

    private fun findProfileByOriginalLowerAndType(
        connection: Connection,
        originalNameLower: String,
        authType: AuthProfileType
    ): AuthProfile? {
        connection.prepareStatement(
            """
            SELECT id, original_name, original_name_lower, internal_name, display_name,
                   uuid, auth_type, premium_bound, created_at, updated_at
            FROM auth_profile
            WHERE original_name_lower = ? AND auth_type = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, originalNameLower)
            statement.setString(2, authType.name)
            statement.executeQuery().use { result ->
                return if (result.next()) mapProfile(result) else null
            }
        }
    }

    private fun findProfileByInternalName(connection: Connection, internalNameLower: String): AuthProfile? {
        connection.prepareStatement(
            """
            SELECT id, original_name, original_name_lower, internal_name, display_name,
                   uuid, auth_type, premium_bound, created_at, updated_at
            FROM auth_profile
            WHERE lower(internal_name) = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, internalNameLower)
            statement.executeQuery().use { result ->
                return if (result.next()) mapProfile(result) else null
            }
        }
    }

    private fun findProfilesByOriginalLower(connection: Connection, originalNameLower: String): List<AuthProfile> {
        connection.prepareStatement(
            """
            SELECT id, original_name, original_name_lower, internal_name, display_name,
                   uuid, auth_type, premium_bound, created_at, updated_at
            FROM auth_profile
            WHERE original_name_lower = ?
            ORDER BY CASE auth_type WHEN 'PREMIUM' THEN 0 ELSE 1 END, updated_at DESC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, originalNameLower)
            statement.executeQuery().use { result ->
                val profiles = mutableListOf<AuthProfile>()
                while (result.next()) {
                    profiles += mapProfile(result)
                }
                return profiles
            }
        }
    }

    private fun deletePending(connection: Connection, id: Long) {
        connection.prepareStatement("DELETE FROM pending_offline_rename WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }
    }

    private fun deleteExpiredPending(connection: Connection, now: Long): Int {
        connection.prepareStatement("DELETE FROM pending_offline_rename WHERE expires_at <= ?").use { statement ->
            statement.setLong(1, now)
            return statement.executeUpdate()
        }
    }

    private fun recordMigration(
        connection: Connection,
        old: AuthProfile,
        newAuthType: AuthProfileType,
        reason: String,
        now: Long
    ) {
        connection.prepareStatement(
            """
            INSERT INTO auth_profile_migration_log (
                original_name, original_name_lower, old_internal_name, old_uuid,
                old_auth_type, old_premium_bound, new_auth_type, reason, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, old.originalName)
            statement.setString(2, old.originalNameLower)
            statement.setString(3, old.internalName)
            statement.setString(4, old.uuid.toString())
            statement.setString(5, old.authType.name)
            statement.setInt(6, if (old.premiumBound) 1 else 0)
            statement.setString(7, newAuthType.name)
            statement.setString(8, reason)
            statement.setLong(9, now)
            statement.executeUpdate()
        }
    }

    private fun migrateAuthProfileDualTypeConstraint(connection: Connection) {
        val existingSql = tableSql(connection, "auth_profile") ?: return
        val normalizedSql = existingSql.replace(Regex("\\s+"), "").uppercase(Locale.ROOT)
        if ("UNIQUE(ORIGINAL_NAME_LOWER,AUTH_TYPE)" in normalizedSql) {
            return
        }
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE IF EXISTS auth_profile_new")
                statement.executeUpdate(authProfileTableSql("auth_profile_new"))
                statement.executeUpdate(
                    """
                    INSERT INTO auth_profile_new (
                        id, original_name, original_name_lower, internal_name, display_name,
                        uuid, auth_type, premium_bound, created_at, updated_at
                    )
                    SELECT id, original_name, original_name_lower, internal_name, display_name,
                           uuid, auth_type, premium_bound, created_at, updated_at
                    FROM auth_profile
                    ORDER BY id
                    """.trimIndent()
                )
                statement.executeUpdate("DROP TABLE auth_profile")
                statement.executeUpdate("ALTER TABLE auth_profile_new RENAME TO auth_profile")
            }
            connection.commit()
            logger.info("Migrated auth_profile unique constraint to (original_name_lower, auth_type).")
        } catch (throwable: Throwable) {
            connection.rollback()
            throw throwable
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun tableSql(connection: Connection, tableName: String): String? {
        connection.prepareStatement(
            """
            SELECT sql
            FROM sqlite_master
            WHERE type = 'table' AND name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { result ->
                return if (result.next()) result.getString("sql") else null
            }
        }
    }

    private fun connection(): Connection {
        val databasePath = dataDirectory.resolve(settingsProvider().storage.sqliteFile)
        Files.createDirectories(databasePath.parent ?: dataDirectory)
        val connection = DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        connection.prepareStatement("PRAGMA busy_timeout = 5000").use { it.execute() }
        return connection
    }

    private fun mapProfile(result: ResultSet): AuthProfile {
        return AuthProfile(
            id = result.getLong("id"),
            originalName = result.getString("original_name"),
            originalNameLower = result.getString("original_name_lower"),
            internalName = result.getString("internal_name"),
            displayName = result.getString("display_name"),
            uuid = UUID.fromString(result.getString("uuid")),
            authType = AuthProfileType.parse(result.getString("auth_type")),
            premiumBound = result.getInt("premium_bound") == 1,
            createdAt = result.getLong("created_at"),
            updatedAt = result.getLong("updated_at")
        )
    }

    private fun mapPending(result: ResultSet): PendingOfflineRename {
        return PendingOfflineRename(
            id = result.getLong("id"),
            username = result.getString("username"),
            usernameLower = result.getString("username_lower"),
            offlineName = result.getString("offline_name"),
            ip = result.getString("ip"),
            expiresAt = result.getLong("expires_at"),
            createdAt = result.getLong("created_at")
        )
    }

    private fun runAsync(block: () -> Unit): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                try {
                    block()
                } catch (throwable: Throwable) {
                    logger.warn("AuthCode Velocity SQLite operation failed: {}", throwable.message)
                    throw throwable
                }
            },
            executor
        )
    }

    private fun <T> supplyAsync(block: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(
            {
                try {
                    block()
                } catch (throwable: Throwable) {
                    logger.warn("AuthCode Velocity SQLite operation failed: {}", throwable.message)
                    throw throwable
                }
            },
            executor
        )
    }

    private fun authProfileTableSql(tableName: String): String {
        return """
        CREATE TABLE IF NOT EXISTS $tableName (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          original_name TEXT NOT NULL,
          original_name_lower TEXT NOT NULL,
          internal_name TEXT NOT NULL UNIQUE,
          display_name TEXT NOT NULL,
          uuid TEXT NOT NULL UNIQUE,
          auth_type TEXT NOT NULL,
          premium_bound INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL,
          UNIQUE(original_name_lower, auth_type)
        )
        """.trimIndent()
    }

    private val schemaStatements = listOf(
        authProfileTableSql("auth_profile"),
        """
        CREATE TABLE IF NOT EXISTS pending_offline_rename (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          username TEXT NOT NULL,
          username_lower TEXT NOT NULL,
          offline_name TEXT NOT NULL,
          ip TEXT NOT NULL,
          expires_at INTEGER NOT NULL,
          created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS idx_pending_offline_rename_username_ip
        ON pending_offline_rename(username_lower, ip)
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS auth_profile_migration_log (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          original_name TEXT NOT NULL,
          original_name_lower TEXT NOT NULL,
          old_internal_name TEXT,
          old_uuid TEXT,
          old_auth_type TEXT,
          old_premium_bound INTEGER,
          new_auth_type TEXT NOT NULL,
          reason TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )
        """.trimIndent()
    )
}
