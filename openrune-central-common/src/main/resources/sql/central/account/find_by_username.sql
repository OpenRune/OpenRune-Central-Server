SELECT a.id, a.login_username AS username, a.password_hash, a.rights
FROM accounts a
WHERE LOWER(a.login_username) = LOWER(?)
LIMIT 1
