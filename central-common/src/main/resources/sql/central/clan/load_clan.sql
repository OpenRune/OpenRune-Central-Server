SELECT c.*, COALESCE(owner.display_name, '') AS owner_display_name
FROM clans c
JOIN account_characters owner ON owner.id = c.owner_character_id
WHERE c.id = ?
