/* Older admin-web builds stored rsmod-style `modlevel.*` tokens, which game servers parsing
   `Rights.fromRightsColumn` read as NONE. Rewrite them to the canonical `Rights` names. */

UPDATE accounts
SET rights = CASE lower(trim(rights))
        WHEN 'modlevel.moderator' THEN 'MOD'
        WHEN 'modlevel.admin' THEN 'ADMINISTRATOR'
        WHEN 'modlevel.owner' THEN 'ADMINISTRATOR'
        ELSE ''
    END
WHERE lower(trim(rights)) LIKE 'modlevel.%';

UPDATE worlds
SET login_min_rights_token = CASE lower(trim(login_min_rights_token))
        WHEN 'modlevel.moderator' THEN 'MOD'
        WHEN 'modlevel.admin' THEN 'ADMINISTRATOR'
        WHEN 'modlevel.owner' THEN 'ADMINISTRATOR'
        ELSE NULL
    END
WHERE lower(trim(login_min_rights_token)) LIKE 'modlevel.%';
