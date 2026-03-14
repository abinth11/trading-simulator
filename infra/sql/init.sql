-- ─────────────────────────────────────────────────────────────────
--  Trading Simulator — Database Schema
--  PostgreSQL 16
-- ─────────────────────────────────────────────────────────────────

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── USERS ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    cash_balance  DECIMAL(18, 2) NOT NULL DEFAULT 100000.00, -- start with 1L virtual cash
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',       -- USER | ADMIN | BOT
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── ORDERS ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    symbol          VARCHAR(20) NOT NULL,                -- e.g. RELIANCE, TCS
    side            VARCHAR(4) NOT NULL,                 -- BUY | SELL
    order_type      VARCHAR(10) NOT NULL DEFAULT 'LIMIT',-- LIMIT | MARKET
    price           DECIMAL(18, 2),                      -- NULL for market orders
    quantity        DECIMAL(18, 6) NOT NULL,
    filled_quantity DECIMAL(18, 6) NOT NULL DEFAULT 0,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING', -- PENDING | PARTIAL | FILLED | CANCELLED | REJECTED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id  ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol   ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_status   ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created  ON orders(created_at DESC);

-- ── TRADES ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS trades (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    buy_order_id  UUID NOT NULL REFERENCES orders(id),
    sell_order_id UUID NOT NULL REFERENCES orders(id),
    buyer_id      UUID NOT NULL REFERENCES users(id),
    seller_id     UUID NOT NULL REFERENCES users(id),
    symbol        VARCHAR(20) NOT NULL,
    price         DECIMAL(18, 2) NOT NULL,
    quantity      DECIMAL(18, 6) NOT NULL,
    executed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trades_symbol      ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_buyer_id    ON trades(buyer_id);
CREATE INDEX IF NOT EXISTS idx_trades_seller_id   ON trades(seller_id);
CREATE INDEX IF NOT EXISTS idx_trades_executed_at ON trades(executed_at DESC);

-- ── PORTFOLIO HOLDINGS ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portfolio_holdings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    symbol          VARCHAR(20) NOT NULL,
    quantity        DECIMAL(18, 6) NOT NULL DEFAULT 0,
    avg_buy_price   DECIMAL(18, 2) NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, symbol)                              -- one row per user per symbol
);

CREATE INDEX IF NOT EXISTS idx_holdings_user_id ON portfolio_holdings(user_id);

-- ── CANDLES (OHLCV) ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS candles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    symbol      VARCHAR(20) NOT NULL,
    interval    VARCHAR(5) NOT NULL,                     -- 1m | 5m | 15m | 1h | 1d
    open_price  DECIMAL(18, 2) NOT NULL,
    high_price  DECIMAL(18, 2) NOT NULL,
    low_price   DECIMAL(18, 2) NOT NULL,
    close_price DECIMAL(18, 2) NOT NULL,
    volume      DECIMAL(18, 6) NOT NULL DEFAULT 0,
    open_time   TIMESTAMPTZ NOT NULL,
    close_time  TIMESTAMPTZ NOT NULL,
    UNIQUE(symbol, interval, open_time)
);

CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval ON candles(symbol, interval);
CREATE INDEX IF NOT EXISTS idx_candles_open_time       ON candles(open_time DESC);

-- ── SYMBOLS (tradeable instruments) ───────────────────────────────
CREATE TABLE IF NOT EXISTS symbols (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticker        VARCHAR(20) NOT NULL UNIQUE,
    company_name  VARCHAR(255) NOT NULL,
    base_price    DECIMAL(18, 2) NOT NULL,               -- starting reference price
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed some symbols
INSERT INTO symbols (ticker, company_name, base_price) VALUES
    ('RELIANCE',  'Reliance Industries Ltd',   2885.00),
    ('TCS',       'Tata Consultancy Services', 3920.00),
    ('INFY',      'Infosys Ltd',               1780.00),
    ('HDFC',      'HDFC Bank Ltd',             1620.00),
    ('WIPRO',     'Wipro Ltd',                  480.00)
ON CONFLICT (ticker) DO NOTHING;

-- ── AUTO-UPDATE updated_at trigger ────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_holdings_updated_at
    BEFORE UPDATE ON portfolio_holdings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
