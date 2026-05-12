SELECT login_username
FROM account_login_aliases
WHERE account_id = ?
ORDER BY id ASC
