SELECT id, login_username AS username, rights, created_at, updated_at
FROM accounts
ORDER BY id DESC
LIMIT ? OFFSET ?
