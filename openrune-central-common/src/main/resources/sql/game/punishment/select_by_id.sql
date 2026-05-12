SELECT

    id, scope, account_id, character_id, kind, issued_at, expires_at,

    reason, private_notes, public_notes, issued_by, approved_by, status, repo_link_uuid

FROM punishments

WHERE id = ?

