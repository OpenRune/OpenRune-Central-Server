UPDATE world_reboot_schedules
SET status = 'cancelled', cancelled_at = CURRENT_TIMESTAMP
WHERE id = ? AND status = 'active'
