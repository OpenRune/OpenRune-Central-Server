SELECT 1 FROM account_login_aliases
WHERE LOWER(login_username) = LOWER(?)
LIMIT 1
