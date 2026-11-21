SELECT i.id, i.clan_id, c.name AS clan_name, i.inviter_character_id,
       COALESCE(inviter.display_name, '') AS inviter_display_name,
       COALESCE((EXTRACT(EPOCH FROM i.expires_at) * 1000)::BIGINT, 0) AS expires_at_millis
FROM clan_invites i
JOIN clans c ON c.id = i.clan_id
JOIN account_characters inviter ON inviter.id = i.inviter_character_id
WHERE i.invitee_character_id = ? AND i.consumed_at IS NULL
  AND (i.expires_at IS NULL OR i.expires_at > CURRENT_TIMESTAMP)
ORDER BY i.created_at DESC, i.id DESC
