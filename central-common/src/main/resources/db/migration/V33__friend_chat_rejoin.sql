-- Durable Friends Chat login restore target.
--
-- Live channel membership is not stored here. This table stores only the channel
-- identity that the server hands back to clientscript 3765 (chatchannel_login) on
-- the character's next login.
CREATE TABLE IF NOT EXISTS friend_chat_restore_targets (
    character_id INTEGER PRIMARY KEY REFERENCES account_characters(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES friend_chat_channels(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_friend_chat_restore_targets_channel
    ON friend_chat_restore_targets(channel_id);
