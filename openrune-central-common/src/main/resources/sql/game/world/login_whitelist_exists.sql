SELECT 1
FROM world_login_whitelist
WHERE world_id = ?
  AND lower(login_username) = lower(?)
LIMIT 1
