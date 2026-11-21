SELECT m.*, COALESCE(ch.display_name, '') AS display_name
FROM clan_members m
JOIN account_characters ch ON ch.id = m.character_id
ORDER BY m.clan_id, m.joined_at, m.character_id
