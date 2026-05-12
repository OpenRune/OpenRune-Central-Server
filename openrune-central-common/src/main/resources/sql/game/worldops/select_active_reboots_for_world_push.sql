SELECT world_id, reboot_at, message
FROM world_reboot_schedules
WHERE status = 'active'
  AND reboot_at > CURRENT_TIMESTAMP
  AND (world_id IS NULL OR world_id = ?)
ORDER BY reboot_at ASC, id ASC
