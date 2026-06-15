package ym.authcode.storage.sqlite

object SQLiteSchema {
    val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS players (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT,
            name TEXT NOT NULL,
            lower_name TEXT NOT NULL UNIQUE,
            password_hash TEXT,
            premium INTEGER DEFAULT NULL,
            registered INTEGER NOT NULL DEFAULT 0,
            invited_by_code TEXT,
            register_ip TEXT,
            last_ip TEXT,
            register_time INTEGER NOT NULL DEFAULT 0,
            last_login_time INTEGER NOT NULL DEFAULT 0,
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
        "CREATE INDEX IF NOT EXISTS idx_players_lower_name ON players(lower_name)",
        "CREATE INDEX IF NOT EXISTS idx_invite_codes_lower_code ON invite_codes(lower_code)",
        "CREATE INDEX IF NOT EXISTS idx_invite_code_uses_lower_code ON invite_code_uses(lower_code)"
    )
}
