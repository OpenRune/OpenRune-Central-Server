INSERT INTO punishment_attached_logs (punishment_id, log_uuid)
VALUES (?, ?)
ON CONFLICT (punishment_id, log_uuid) DO NOTHING
