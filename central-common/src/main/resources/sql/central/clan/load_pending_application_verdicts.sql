SELECT a.id, COALESCE(a.clan_id, 0) AS clan_id, a.clan_name, a.status,
       COALESCE((EXTRACT(EPOCH FROM a.reviewed_at) * 1000)::BIGINT, 0) AS reviewed_at_millis
FROM clan_applications a
WHERE a.applicant_character_id = ? AND a.status IN (1, 2, 4) AND a.notified_at IS NULL
ORDER BY a.reviewed_at, a.id
