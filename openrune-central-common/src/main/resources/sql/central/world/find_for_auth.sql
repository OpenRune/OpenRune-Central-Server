SELECT world_id,
       enabled,
       max_players,
       world_key_sha256,
       login_restrictions_enabled,
       login_min_total_level,
       login_min_rights_token,
       login_gate_min_level_enabled,
       login_gate_rights_enabled,
       login_gate_whitelist_enabled
FROM worlds
WHERE world_id = ?
