-- Durable selected guest Clan state.
--
-- Live channel presence is not stored here. This stores only the selected
-- guest Clan identity that should be restored on the next login.

CREATE TABLE IF NOT EXISTS clan_guest_selections (
    character_id INTEGER PRIMARY KEY
        REFERENCES account_characters(id) ON DELETE CASCADE,
    clan_id BIGINT NOT NULL
        REFERENCES clans(id) ON DELETE CASCADE,
    selected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clan_guest_selections_clan
    ON clan_guest_selections(clan_id);
