-- ─────────────────────────────────────────────────────────────────
--  001 — Cash / holdings reservation for open orders
--  Apply to an existing database:
--    docker exec -i trading-postgres psql -U tradingsim -d tradingsim < infra/sql/migrations/001_order_reservations.sql
--  (Fresh databases get this from init.sql.)
-- ─────────────────────────────────────────────────────────────────

-- Worst price an order may execute at: the limit price, or the protection cap for MARKET orders.
-- Open BUY orders reserve remaining quantity x price_cap of the user's cash.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS price_cap DECIMAL(18, 2);
UPDATE orders SET price_cap = price WHERE price_cap IS NULL AND price IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_open_by_user
    ON orders(user_id, side, symbol) WHERE status IN ('PENDING', 'PARTIAL');

-- One row per trade that portfolio-service has applied to cash and holdings.
-- Trades without a row are executed but unsettled, and still count as reserved.
CREATE TABLE IF NOT EXISTS trade_settlements (
    trade_id   UUID PRIMARY KEY,
    settled_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Every trade that exists before this migration was already applied by portfolio-service
INSERT INTO trade_settlements (trade_id)
SELECT id FROM trades
ON CONFLICT (trade_id) DO NOTHING;
