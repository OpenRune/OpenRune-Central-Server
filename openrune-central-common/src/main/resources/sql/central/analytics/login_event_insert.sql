INSERT INTO login_events (account_id, world_id, day_utc, first_at)
VALUES (?, ?, ?, ?)
ON CONFLICT (account_id, day_utc) DO NOTHING
