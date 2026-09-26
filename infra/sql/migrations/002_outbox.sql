-- ─────────────────────────────────────────────────────────────────
--  002 — Transactional outbox
--  Apply to an existing database:
--    docker exec -i trading-postgres psql -U tradingsim -d tradingsim < infra/sql/migrations/002_outbox.sql
--  (Fresh databases get this from init.sql.)
-- ─────────────────────────────────────────────────────────────────

-- Kafka events waiting to be published. Services insert them in the same transaction as the
-- change they describe; each service's relay publishes its own rows after commit, then deletes them.
CREATE TABLE IF NOT EXISTS outbox_events (
    id         BIGSERIAL PRIMARY KEY,               -- publish order
    producer   VARCHAR(40)  NOT NULL,               -- service that owns the row
    topic      VARCHAR(100) NOT NULL,
    event_key  VARCHAR(100),
    payload    TEXT         NOT NULL,               -- JSON
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_producer ON outbox_events(producer, id);
