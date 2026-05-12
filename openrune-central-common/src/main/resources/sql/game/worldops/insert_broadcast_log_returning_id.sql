INSERT INTO world_broadcast_log (world_id, message, url, icon, created_by)
VALUES (?, ?, ?, ?, ?)
RETURNING id
