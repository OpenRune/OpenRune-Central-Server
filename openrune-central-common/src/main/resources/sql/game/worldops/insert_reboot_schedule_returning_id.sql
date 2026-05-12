INSERT INTO world_reboot_schedules (world_id, reboot_at, message, created_by)
VALUES (?, ?::timestamptz, ?, ?)
RETURNING id
