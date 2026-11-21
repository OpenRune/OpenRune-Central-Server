SELECT a.id, a.clan_id, a.applicant_character_id, COALESCE(ch.display_name, '') AS applicant_display_name,
       (EXTRACT(EPOCH FROM a.applied_at) * 1000)::BIGINT AS applied_at_millis,
       a.applicant_combat_level, a.applicant_skill_total, a.applicant_quest_points,
       a.applicant_iron_status, a.applicant_skill_levels
FROM clan_applications a
JOIN account_characters ch ON ch.id = a.applicant_character_id
WHERE a.status = 0 AND a.clan_id = ?
ORDER BY a.applied_at, a.id
