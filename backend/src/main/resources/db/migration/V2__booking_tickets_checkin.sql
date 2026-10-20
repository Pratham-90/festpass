-- Holds, orders, payment attempts, tickets, check-in scans and stress-arena runs.

CREATE TABLE holds (
    id         UUID PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    tier_id    BIGINT REFERENCES ticket_tiers (id), -- general admission only
    quantity   INT CHECK (quantity > 0),             -- general admission only
    status     VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'CONVERTED', 'EXPIRED', 'RELEASED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((tier_id IS NULL) = (quantity IS NULL))
);

CREATE INDEX holds_active_expiry_idx ON holds (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX holds_user_event_idx ON holds (user_id, event_id);

ALTER TABLE seats ADD CONSTRAINT seats_hold_fk FOREIGN KEY (hold_id) REFERENCES holds (id);

CREATE TABLE orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    event_id        BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    hold_id         UUID         NOT NULL UNIQUE REFERENCES holds (id), -- one order per hold
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    amount_paise    BIGINT       NOT NULL CHECK (amount_paise >= 0),
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    created_at      TIMESTAMPTZ  NOT NULL,
    paid_at         TIMESTAMPTZ
);

CREATE INDEX orders_user_idx ON orders (user_id);
CREATE INDEX orders_event_idx ON orders (event_id);

CREATE TABLE payment_attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    outcome         VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payment_attempts_order_idx ON payment_attempts (order_id);

CREATE TABLE tickets (
    id            UUID PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    event_id      BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    tier_id       BIGINT       NOT NULL REFERENCES ticket_tiers (id),
    seat_id       BIGINT REFERENCES seats (id),
    holder_name   VARCHAR(100) NOT NULL,
    checked_in_at TIMESTAMPTZ,
    checked_in_by BIGINT REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- The database's last line of defence against double booking: one ticket per seat, ever.
CREATE UNIQUE INDEX tickets_seat_unique_idx ON tickets (seat_id) WHERE seat_id IS NOT NULL;
CREATE INDEX tickets_order_idx ON tickets (order_id);
CREATE INDEX tickets_event_idx ON tickets (event_id);

CREATE TABLE checkin_scans (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    ticket_id  UUID REFERENCES tickets (id) ON DELETE CASCADE,
    scanned_by BIGINT      NOT NULL REFERENCES users (id),
    result     VARCHAR(20) NOT NULL CHECK (result IN ('VALID', 'ALREADY_USED', 'INVALID', 'WRONG_EVENT')),
    scanned_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX checkin_scans_event_idx ON checkin_scans (event_id, scanned_at);

CREATE TABLE arena_runs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sandbox_event_id BIGINT REFERENCES events (id) ON DELETE SET NULL,
    bots             INT         NOT NULL CHECK (bots > 0),
    strategy         VARCHAR(30) NOT NULL CHECK (strategy IN ('RUSH_SAME_SEATS', 'RANDOM_SEATS', 'GA_RUSH')),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PREPARING', 'RUNNING', 'FINISHED', 'FAILED')),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    result_json      JSONB
);
