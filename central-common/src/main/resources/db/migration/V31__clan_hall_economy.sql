-- Durable clan hall economic state.
-- Player inventories remain game-server-owned; clan storage and transfer reservations are Central-owned.

-- Generic game-server receipts. These are written in the same transaction as character inventory
-- state and reconciled idempotently with Central after reconnect/crash recovery.
CREATE TABLE IF NOT EXISTS character_operation_receipts (
    character_id INTEGER NOT NULL REFERENCES account_characters(id) ON DELETE CASCADE,
    namespace VARCHAR(64) NOT NULL CHECK (length(trim(namespace)) > 0),
    operation_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (character_id, namespace, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_character_operation_receipts_namespace
    ON character_operation_receipts (namespace, character_id);

CREATE TABLE IF NOT EXISTS clan_coffers (
    clan_id BIGINT PRIMARY KEY REFERENCES clans(id) ON DELETE CASCADE,
    coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (coin_balance >= 0 AND coin_balance <= 2147483647),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clan_party_chest_items (
    clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    slot INTEGER NOT NULL CHECK (slot >= 0 AND slot < 216),
    item_id INTEGER NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0 AND quantity <= 2147483647),
    vars INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, slot)
);

CREATE TABLE IF NOT EXISTS clan_party_drop_batches (
    id BIGSERIAL PRIMARY KEY,
    clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL,
    state SMALLINT NOT NULL CHECK (state IN (0, 1, 2, 3)), -- 0 preparing, 1 active, 2 finished, 3 aborted
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_clan_party_drop_one_open_batch
    ON clan_party_drop_batches (clan_id)
    WHERE state IN (0, 1);

CREATE TABLE IF NOT EXISTS clan_party_drop_items (
    batch_id BIGINT NOT NULL REFERENCES clan_party_drop_batches(id) ON DELETE CASCADE,
    source_slot INTEGER NOT NULL CHECK (source_slot >= 0 AND source_slot < 216),
    item_id INTEGER NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0 AND quantity <= 2147483647),
    vars INTEGER NOT NULL DEFAULT 0,
    state SMALLINT NOT NULL DEFAULT 0 CHECK (state IN (0, 1, 2)), -- 0 available, 1 reserved, 2 claimed
    reserved_by_operation UUID NULL,
    PRIMARY KEY (batch_id, source_slot)
);

CREATE TABLE IF NOT EXISTS clan_economy_operations (
    operation_id UUID PRIMARY KEY,
    -- Keep completed operation rows after clan deletion so a stale game-server receipt can still
    -- reconcile idempotently. PREPARED operations/current value block disband in the service layer.
    clan_id BIGINT NOT NULL,
    actor_character_id INTEGER NOT NULL REFERENCES account_characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL,
    operation_type SMALLINT NOT NULL CHECK (operation_type IN (1, 2, 3, 4, 5)),
    state SMALLINT NOT NULL DEFAULT 0 CHECK (state IN (0, 1, 2)), -- 0 prepared, 1 committed, 2 aborted
    item_id INTEGER NOT NULL DEFAULT 0,
    quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0 AND quantity <= 2147483647),
    item_vars INTEGER NOT NULL DEFAULT 0,
    source_slot INTEGER NOT NULL DEFAULT -1,
    target_slot INTEGER NOT NULL DEFAULT -1,
    batch_id BIGINT NULL REFERENCES clan_party_drop_batches(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clan_economy_operations_actor_state
    ON clan_economy_operations (actor_character_id, state);
CREATE INDEX IF NOT EXISTS idx_clan_economy_operations_clan_state
    ON clan_economy_operations (clan_id, state);
CREATE INDEX IF NOT EXISTS idx_clan_party_drop_items_batch_state
    ON clan_party_drop_items (batch_id, state);
