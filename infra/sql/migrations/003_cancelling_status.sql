-- ─────────────────────────────────────────────────────────────────
--  003 — CANCELLING order status
--  Apply to an existing database:
--    docker exec -i trading-postgres psql -U tradingsim -d tradingsim < infra/sql/migrations/003_cancelling_status.sql
--  (Fresh databases get this from init.sql.)
-- ─────────────────────────────────────────────────────────────────

-- A user cancel now sets CANCELLING; the matching engine moves it to CANCELLED (or it ends FILLED).
-- CANCELLING orders still reserve funds, so the open-order index must cover them.
DROP INDEX IF EXISTS idx_orders_open_by_user;
CREATE INDEX idx_orders_open_by_user
    ON orders(user_id, side, symbol) WHERE status IN ('PENDING', 'PARTIAL', 'CANCELLING');
