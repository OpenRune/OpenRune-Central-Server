SELECT EXISTS(
    SELECT 1
    FROM world_login_whitelist
    WHERE world_id = ?
    LIMIT 1
) AS has_rows
