INSERT INTO account_characters (account_id, realm_id, display_name)
VALUES (?, ?, (SELECT login_username FROM accounts WHERE id = ?))
