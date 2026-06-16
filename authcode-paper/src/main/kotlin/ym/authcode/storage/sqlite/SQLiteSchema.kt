package ym.authcode.storage.sqlite

object SQLiteSchema {
    val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS players (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT UNIQUE,
            name TEXT NOT NULL,
            lower_name TEXT NOT NULL UNIQUE,
            original_name TEXT,
            internal_name TEXT,
            lower_internal_name TEXT,
            display_name TEXT,
            password_hash TEXT,
            premium INTEGER DEFAULT NULL,
            registered INTEGER NOT NULL DEFAULT 0,
            invited_by_code TEXT,
            register_ip TEXT,
            last_ip TEXT,
            register_time INTEGER NOT NULL DEFAULT 0,
            last_login_time INTEGER NOT NULL DEFAULT 0,
            auth_source TEXT,
            last_proxy_premium INTEGER,
            last_proxy_verify_time INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS invite_codes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL,
            lower_code TEXT NOT NULL UNIQUE,
            max_uses INTEGER NOT NULL,
            used_count INTEGER NOT NULL DEFAULT 0,
            created_by TEXT NOT NULL,
            created_time INTEGER NOT NULL,
            expire_time INTEGER,
            enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS invite_code_uses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL,
            lower_code TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL,
            ip TEXT NOT NULL,
            use_time INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS proxy_auth_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT NOT NULL,
            name TEXT NOT NULL,
            original_name TEXT,
            internal_name TEXT,
            display_name TEXT,
            premium INTEGER NOT NULL,
            auth_source TEXT NOT NULL,
            remote_ip TEXT NOT NULL,
            server_name TEXT NOT NULL,
            nonce TEXT NOT NULL,
            verify_time INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_players_lower_name ON players(lower_name)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_players_lower_internal_name ON players(lower_internal_name)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_players_uuid ON players(uuid) WHERE uuid IS NOT NULL",
        "CREATE INDEX IF NOT EXISTS idx_invite_codes_lower_code ON invite_codes(lower_code)",
        "CREATE INDEX IF NOT EXISTS idx_invite_code_uses_lower_code ON invite_code_uses(lower_code)",
        "CREATE INDEX IF NOT EXISTS idx_proxy_auth_logs_uuid ON proxy_auth_logs(uuid)",
        "CREATE INDEX IF NOT EXISTS idx_proxy_auth_logs_nonce ON proxy_auth_logs(nonce)"
    )

    val migrations = listOf(
        ColumnMigration("players", "auth_source", "TEXT"),
        ColumnMigration("players", "last_proxy_premium", "INTEGER"),
        ColumnMigration("players", "last_proxy_verify_time", "INTEGER NOT NULL DEFAULT 0"),
        ColumnMigration("players", "original_name", "TEXT"),
        ColumnMigration("players", "internal_name", "TEXT"),
        ColumnMigration("players", "lower_internal_name", "TEXT"),
        ColumnMigration("players", "display_name", "TEXT"),
        ColumnMigration("proxy_auth_logs", "original_name", "TEXT"),
        ColumnMigration("proxy_auth_logs", "internal_name", "TEXT"),
        ColumnMigration("proxy_auth_logs", "display_name", "TEXT")
    )
}

data class ColumnMigration(
    val table: String,
    val column: String,
    val definition: String
)
