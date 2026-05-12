SELECT 1 FROM accounts
WHERE LOWER(login_username) = LOWER(?) AND id != ?
LIMIT 1
