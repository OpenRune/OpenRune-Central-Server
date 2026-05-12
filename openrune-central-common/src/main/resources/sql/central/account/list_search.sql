SELECT id, login_username AS username, rights, created_at, updated_at
FROM accounts
WHERE login_username LIKE ? ESCAPE '\'
ORDER BY id DESC
LIMIT ? OFFSET ?
