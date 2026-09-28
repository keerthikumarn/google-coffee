-- Google Coffee schema for the "local" (PostgreSQL) profile.
-- Timestamps are epoch milliseconds to match the Firestore model exactly.

CREATE TABLE menu_items (
    id            TEXT PRIMARY KEY,
    name          TEXT    NOT NULL,
    category      TEXT    NOT NULL,
    description   TEXT    NOT NULL DEFAULT '',
    price         INTEGER NOT NULL CHECK (price >= 0),
    prep_minutes  INTEGER NOT NULL CHECK (prep_minutes >= 0),
    tags          JSONB   NOT NULL DEFAULT '[]'::jsonb,
    popular       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE sessions (
    id           TEXT PRIMARY KEY,
    name         TEXT   NOT NULL,
    table_no     TEXT   NOT NULL,
    preferences  JSONB  NOT NULL DEFAULT '[]'::jsonb,
    created_at   BIGINT NOT NULL
);

CREATE TABLE orders (
    id             TEXT PRIMARY KEY,
    code           TEXT    NOT NULL,
    session_id     TEXT    NOT NULL REFERENCES sessions (id),
    customer_name  TEXT    NOT NULL,
    table_no       TEXT    NOT NULL,
    items          JSONB   NOT NULL,
    total          INTEGER NOT NULL,
    prep_minutes   INTEGER NOT NULL,
    status         TEXT    NOT NULL
                   CHECK (status IN ('PLACED', 'PREPARING', 'READY', 'COLLECTED', 'CANCELLED')),
    created_at     BIGINT  NOT NULL,
    updated_at     BIGINT  NOT NULL
);
CREATE INDEX orders_status_idx  ON orders (status);
CREATE INDEX orders_session_idx ON orders (session_id);

CREATE TABLE feedback (
    id          TEXT PRIMARY KEY,
    session_id  TEXT     NOT NULL REFERENCES sessions (id),
    order_id    TEXT,
    table_no    TEXT     NOT NULL,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT     NOT NULL DEFAULT '',
    created_at  BIGINT   NOT NULL
);
CREATE INDEX feedback_created_idx ON feedback (created_at);

CREATE TABLE settings (
    key    TEXT PRIMARY KEY,
    value  TEXT NOT NULL
);