SELECT DISTINCT a.id, a.login_username AS username, a.password_hash, a.rights
FROM accounts a
LEFT JOIN account_login_aliases al ON al.account_id = a.id
WHERE LOWER(a.login_username) = LOWER(?)
   OR LOWER(al.login_username) = LOWER(?)
LIMIT 1
