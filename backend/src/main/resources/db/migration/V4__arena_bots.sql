-- Stress Arena bots are ordinary user accounts marked as bots, so they can be hidden from
-- the admin user list and deleted by "Reset arena".
ALTER TABLE users ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX users_bots_idx ON users (id) WHERE is_bot;
CREATE INDEX events_sandbox_idx ON events (id) WHERE is_sandbox;
