SELECT online_central_world_id
FROM account_characters
WHERE id = ?
  AND online_central_world_id IS NOT NULL
  AND online_session_heartbeat >= ?
