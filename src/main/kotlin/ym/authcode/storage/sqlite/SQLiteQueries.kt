package ym.authcode.storage.sqlite

object SQLiteQueries {
    const val FIND_PLAYER_BY_LOWER_NAME = """
        SELECT uuid, name, lower_name, password_hash, premium, registered, invited_by_code,
               register_ip, last_ip, register_time, last_login_time, created_at, updated_at
        FROM players
        WHERE lower_name = ?
    """

    const val SAVE_REGISTERED_PLAYER = """
        INSERT INTO players (
            uuid, name, lower_name, password_hash, premium, registered, invited_by_code,
            register_ip, last_ip, register_time, last_login_time, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, NULL, 1, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(lower_name) DO UPDATE SET
            uuid = excluded.uuid,
            name = excluded.name,
            password_hash = excluded.password_hash,
            registered = 1,
            invited_by_code = excluded.invited_by_code,
            register_ip = CASE
                WHEN players.register_ip IS NULL OR players.register_ip = '' THEN excluded.register_ip
                ELSE players.register_ip
            END,
            last_ip = excluded.last_ip,
            register_time = CASE
                WHEN players.register_time <= 0 THEN excluded.register_time
                ELSE players.register_time
            END,
            last_login_time = excluded.last_login_time,
            updated_at = excluded.updated_at
    """

    const val UPDATE_LOGIN = """
        UPDATE players
        SET last_ip = ?, last_login_time = ?, updated_at = ?
        WHERE lower_name = ?
    """

    const val UPDATE_PASSWORD = """
        UPDATE players
        SET password_hash = ?, updated_at = ?
        WHERE lower_name = ?
    """

    const val SET_PREMIUM_OVERRIDE = """
        INSERT INTO players (
            uuid, name, lower_name, password_hash, premium, registered, invited_by_code,
            register_ip, last_ip, register_time, last_login_time, created_at, updated_at
        )
        VALUES (NULL, ?, ?, NULL, ?, 0, NULL, NULL, NULL, 0, 0, ?, ?)
        ON CONFLICT(lower_name) DO UPDATE SET
            name = excluded.name,
            premium = excluded.premium,
            updated_at = excluded.updated_at
    """

    const val CREATE_INVITE_CODE = """
        INSERT OR IGNORE INTO invite_codes (
            code, lower_code, max_uses, used_count, created_by, created_time,
            expire_time, enabled, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    const val DELETE_INVITE_CODE = "DELETE FROM invite_codes WHERE lower_code = ?"

    const val FIND_INVITE_CODE = """
        SELECT code, lower_code, max_uses, used_count, created_by, created_time,
               expire_time, enabled, created_at, updated_at
        FROM invite_codes
        WHERE lower_code = ?
    """

    const val LIST_INVITE_CODES = """
        SELECT code, lower_code, max_uses, used_count, created_by, created_time,
               expire_time, enabled, created_at, updated_at
        FROM invite_codes
        ORDER BY created_time DESC
        LIMIT 100
    """

    const val UPDATE_INVITE_USED = """
        UPDATE invite_codes
        SET used_count = used_count + 1, updated_at = ?
        WHERE lower_code = ? AND used_count < max_uses
    """

    const val INSERT_INVITE_USE = """
        INSERT INTO invite_code_uses (code, lower_code, player_uuid, player_name, ip, use_time)
        VALUES (?, ?, ?, ?, ?, ?)
    """
}
