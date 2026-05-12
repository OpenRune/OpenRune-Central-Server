SELECT

    p.id, p.scope, p.account_id, p.character_id, p.kind, p.issued_at, p.expires_at,

    p.reason, p.private_notes, p.public_notes, p.issued_by, p.approved_by, p.status, p.repo_link_uuid

FROM punishments p

WHERE (p.scope = 'account' AND p.account_id = ?)

   OR (

     p.scope = 'character'

     AND p.character_id IN (SELECT c.id FROM account_characters c WHERE c.account_id = ?)

   )

ORDER BY p.issued_at DESC

