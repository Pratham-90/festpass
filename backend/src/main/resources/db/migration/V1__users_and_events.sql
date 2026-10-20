-- Users, events, ticket tiers, seats and gate staff assignments.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE, -- always stored lower-case
    password_hash VARCHAR(100) NOT NULL,        -- BCrypt
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'ORGANIZER', 'GATE_STAFF', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE events (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organizer_id         BIGINT        NOT NULL REFERENCES users (id),
    slug                 VARCHAR(120)  NOT NULL UNIQUE,
    title                VARCHAR(150)  NOT NULL,
    description          VARCHAR(5000),
    category             VARCHAR(20)   NOT NULL
        CHECK (category IN ('MUSIC', 'DANCE', 'TECH', 'COMEDY', 'DRAMA', 'SPORTS', 'WORKSHOP', 'OTHER')),
    banner_url           VARCHAR(500),
    venue_name           VARCHAR(150)  NOT NULL,
    starts_at            TIMESTAMPTZ   NOT NULL,
    ends_at              TIMESTAMPTZ   NOT NULL,
    sale_opens_at        TIMESTAMPTZ   NOT NULL,
    sale_closes_at       TIMESTAMPTZ   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    seating_mode         VARCHAR(20)   NOT NULL CHECK (seating_mode IN ('SEATED', 'GENERAL')),
    hold_minutes         INT           NOT NULL DEFAULT 5 CHECK (hold_minutes BETWEEN 1 AND 30),
    max_tickets_per_user INT           NOT NULL DEFAULT 4 CHECK (max_tickets_per_user BETWEEN 1 AND 20),
    flash_drop           BOOLEAN       NOT NULL DEFAULT FALSE,
    is_sandbox           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ   NOT NULL,
    CHECK (ends_at > starts_at),
    CHECK (sale_closes_at > sale_opens_at)
);

CREATE INDEX events_listing_idx ON events (status, starts_at) WHERE is_sandbox = FALSE;
CREATE INDEX events_organizer_idx ON events (organizer_id);

CREATE TABLE ticket_tiers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    price_paise BIGINT      NOT NULL CHECK (price_paise >= 0),
    capacity    INT         NOT NULL CHECK (capacity >= 0),
    -- held + sold. The CHECK is a last line of defence against overselling a GA tier.
    reserved    INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= capacity),
    color_hex   VARCHAR(7)  NOT NULL CHECK (color_hex ~ '^#[0-9a-fA-F]{6}$'),
    UNIQUE (event_id, name)
);

CREATE INDEX ticket_tiers_event_idx ON ticket_tiers (event_id);

CREATE TABLE seats (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    tier_id         BIGINT      NOT NULL REFERENCES ticket_tiers (id),
    section         VARCHAR(40) NOT NULL,
    row_label       VARCHAR(5)  NOT NULL,
    seat_number     INT         NOT NULL CHECK (seat_number > 0),
    x               INT         NOT NULL,
    y               INT         NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_id         UUID,
    hold_expires_at TIMESTAMPTZ,
    UNIQUE (event_id, section, row_label, seat_number),
    -- An available seat never points at a hold; a held or booked seat always does.
    CHECK ((status = 'AVAILABLE') = (hold_id IS NULL)),
    CHECK (status <> 'HELD' OR hold_expires_at IS NOT NULL)
);

CREATE INDEX seats_hold_idx ON seats (hold_id) WHERE hold_id IS NOT NULL;
CREATE INDEX seats_tier_idx ON seats (tier_id);

CREATE TABLE gate_assignments (
    user_id  BIGINT NOT NULL REFERENCES users (id),
    event_id BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX gate_assignments_event_idx ON gate_assignments (event_id);
