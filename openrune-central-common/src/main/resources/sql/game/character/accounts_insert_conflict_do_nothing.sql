INSERT INTO accounts (login_username, password_hash)
VALUES (?, ?)
ON CONFLICT ((lower(login_username))) DO NOTHING
