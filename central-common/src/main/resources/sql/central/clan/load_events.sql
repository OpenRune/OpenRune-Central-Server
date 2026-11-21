SELECT e.id, e.clan_id, e.position, e.event_date, e.event_time, e.duration_days, e.world,
       e.type, e.subtype, e.activity, e.rank_plus_one, e.creator_character_id, e.creator_display_name
FROM clan_events e
ORDER BY e.clan_id, e.position, e.id
