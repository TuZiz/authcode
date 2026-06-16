package ym.authcode.storage.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.common.model.PlayerIdentity
import ym.authcode.config.ConfigManager
import ym.authcode.model.InviteCode
import ym.authcode.model.InviteCodeUse
import ym.authcode.model.InviteUseResult
import ym.authcode.model.PlayerAuthData
import ym.authcode.model.ProxyAuthLog
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.storage.Storage
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

class SQLiteStorage(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val scheduler: SchedulerAdapter
) : Storage {
    @Volatile
    private var dataSource: HikariDataSource? = null
    private lateinit var players: SQLitePlayers
    private lateinit var inviteCodes: SQLiteInviteCodes

    override fun initialize(): CompletableFuture<Void> {
        return runAsync {
            val databaseFile = File(plugin.dataFolder, configManager.current().storage.sqliteFile)
            databaseFile.parentFile?.mkdirs()
            val hikari = HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
                maximumPoolSize = 1
                poolName = "AuthCodeSQLite"
                connectionTestQuery = "SELECT 1"
                addDataSourceProperty("foreign_keys", "true")
            })
            dataSource = hikari
            hikari.connection.use { connection ->
                SQLiteSchema.statements.forEach { sql ->
                    connection.prepareStatement(sql).use { it.executeUpdate() }
                }
                SQLiteSchema.migrations.forEach { migration ->
                    if (!hasColumn(connection, migration.table, migration.column)) {
                        connection.prepareStatement(
                            "ALTER TABLE ${migration.table} ADD COLUMN ${migration.column} ${migration.definition}"
                        ).use { it.executeUpdate() }
                    }
                }
            }
            players = SQLitePlayers(hikari)
            inviteCodes = SQLiteInviteCodes(hikari)
        }
    }

    override fun findPlayerByLowerName(lowerName: String): CompletableFuture<PlayerAuthData?> {
        return supplyAsync { players.findByLowerName(lowerName) }
    }

    override fun saveRegisteredPlayer(
        identity: PlayerIdentity,
        passwordHash: String,
        invitedByCode: String,
        ip: String,
        now: Long
    ): CompletableFuture<Void> {
        return runAsync { players.saveRegistered(identity, passwordHash, invitedByCode, ip, now) }
    }

    override fun updateLogin(lowerName: String, ip: String, now: Long): CompletableFuture<Void> {
        return runAsync { players.updateLogin(lowerName, ip, now) }
    }

    override fun updatePassword(lowerName: String, passwordHash: String, now: Long): CompletableFuture<Void> {
        return runAsync { players.updatePassword(lowerName, passwordHash, now) }
    }

    override fun setPremiumOverride(
        name: String,
        lowerName: String,
        premium: Boolean,
        now: Long
    ): CompletableFuture<Void> {
        return runAsync { players.setPremiumOverride(name, lowerName, premium, now) }
    }

    override fun updateProxyAuthStatus(
        identity: PlayerIdentity,
        authSource: String,
        now: Long
    ): CompletableFuture<Void> {
        return runAsync { players.updateProxyAuthStatus(identity, authSource, now) }
    }

    override fun recordProxyAuthLog(log: ProxyAuthLog): CompletableFuture<Void> {
        return runAsync { players.recordProxyAuthLog(log) }
    }

    override fun createInviteCode(code: InviteCode): CompletableFuture<Boolean> {
        return supplyAsync { inviteCodes.create(code) }
    }

    override fun deleteInviteCode(lowerCode: String): CompletableFuture<Boolean> {
        return supplyAsync { inviteCodes.delete(lowerCode) }
    }

    override fun findInviteCode(lowerCode: String): CompletableFuture<InviteCode?> {
        return supplyAsync { inviteCodes.find(lowerCode) }
    }

    override fun listInviteCodes(): CompletableFuture<List<InviteCode>> {
        return supplyAsync { inviteCodes.list() }
    }

    override fun listInviteCodeUses(lowerCode: String): CompletableFuture<List<InviteCodeUse>> {
        return supplyAsync { inviteCodes.listUses(lowerCode) }
    }

    override fun useInviteCode(
        code: String,
        playerUuid: UUID,
        playerName: String,
        ip: String,
        now: Long
    ): CompletableFuture<InviteUseResult> {
        return supplyAsync { inviteCodes.use(code, playerUuid, playerName, ip, now) }
    }

    override fun close() {
        dataSource?.close()
        dataSource = null
    }

    private fun runAsync(block: () -> Unit): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        scheduler.runAsync {
            try {
                block()
                future.complete(null)
            } catch (throwable: Throwable) {
                plugin.logger.severe("SQLite operation failed: ${throwable.message}")
                throwable.printStackTrace()
                future.completeExceptionally(throwable)
            }
        }
        return future
    }

    private fun hasColumn(connection: java.sql.Connection, table: String, column: String): Boolean {
        connection.prepareStatement("PRAGMA table_info($table)").use { statement ->
            statement.executeQuery().use { result ->
                while (result.next()) {
                    if (result.getString("name") == column) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun <T> supplyAsync(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        scheduler.runAsync {
            try {
                future.complete(block())
            } catch (throwable: Throwable) {
                plugin.logger.severe("SQLite operation failed: ${throwable.message}")
                throwable.printStackTrace()
                future.completeExceptionally(throwable)
            }
        }
        return future
    }
}
