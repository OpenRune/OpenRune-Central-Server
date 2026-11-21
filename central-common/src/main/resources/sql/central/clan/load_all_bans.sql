SELECT b.id, b.clan_id, b.character_id, b.display_name, b.banned_by_character_id,
       (EXTRACT(EPOCH FROM b.created_at) * 1000)::BIGINT AS created_at_millis
FROM clan_bans b
ORDER BY b.clan_id, b.created_at, b.id
