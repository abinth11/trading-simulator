# Trading Simulator

A stock exchange simulator built with Java 21, Spring Boot 3.2, PostgreSQL, Redis and Kafka,
with a React admin dashboard for operating it.

---

## Architecture

```
Admin dashboard (React, :5173) ──── Vite proxy ────┐
                                                   │
┌──────────────────────────────────────────────────┴───────┐
│  User Service       :8081   auth, JWT, accounts          │
│  Order Service      :8082   order validation, funds      │
│                             reservation, market simulator│
│  Matching Engine    :8083   one order book per symbol    │
│  Portfolio Service  :8084   holdings, cash, PnL          │
└──────────────────────────────────────────────────────────┘
        │ order.placed / order.cancelled      ▲
        ▼                                     │
   Matching Engine ── trade.executed ──► Portfolio Service
                   ── price.updated
             (Kafka, published via a transactional outbox)
```

All services share one PostgreSQL database (`tradingsim`). Redis caches the last traded price.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Message bus | Kafka (Confluent) |
| Auth | JWT (JJWT 0.12) |
| Dashboard | React 18 + TypeScript + Vite |
| Build | Maven (multi-module) |
| Observability | Prometheus + Grafana |

---

## Getting Started

### Prerequisites
- Java 21+, Maven 3.9+
- Node 20+
- Docker + Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
docker compose ps        # wait for postgres and redis to be healthy
```

A new database volume is initialised from `infra/sql/init.sql`.

**Existing database?** Apply the migrations it hasn't had yet, in order:

```bash
for f in infra/sql/migrations/*.sql; do
  docker exec -i trading-postgres psql -U tradingsim -d tradingsim < "$f"
done
```

The migrations are written to be safe to re-run.

### 2. Build and run the services

```bash
mvn -DskipTests package

java -jar services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar services/order-service/target/order-service-1.0.0-SNAPSHOT.jar
java -jar services/matching-engine/target/matching-engine-1.0.0-SNAPSHOT.jar
java -jar services/portfolio-service/target/portfolio-service-1.0.0-SNAPSHOT.jar
```

Each service's health is at `http://localhost:<port>/actuator/health`.

Stop a service before rebuilding its jar. Replacing the jar under a running JVM breaks class loading.

### 3. Run the admin dashboard

```bash
cd admin-dashboard
npm install
npm run dev              # http://localhost:5173
```

Sign in with the operator account user-service creates on startup:

| Email | Password |
|---|---|
| `admin@tradingsim.dev` | `admin12345` |

These are local-dev defaults. Override them with `ADMIN_EMAIL` / `ADMIN_PASSWORD` on user-service
(the account is only created if the email is unused).

Start the market simulator from the dashboard's Trading tab to generate order flow.

### 4. Try the API

```bash
# Register (every new account starts with ₹1,00,000)
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"trader@example.com","username":"trader1","password":"password123"}'

# Place a limit order (use the accessToken from the response above)
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"symbol":"INFY","side":"BUY","orderType":"LIMIT","price":1750,"quantity":2}'

# Market order: no price
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"symbol":"INFY","side":"BUY","orderType":"MARKET","quantity":1}'

# Portfolio
curl http://localhost:8084/api/v1/portfolio -H "Authorization: Bearer TOKEN"
```

### 5. Run tests

```bash
mvn test                               # all services
mvn test -pl services/matching-engine  # one service
cd admin-dashboard && npm run typecheck
```

---

## Project Structure

```
trading-simulator/
├── pom.xml                      ← parent POM
├── docker-compose.yml           ← local infrastructure
├── admin-dashboard/             ← React operator dashboard
├── infra/
│   ├── sql/init.sql             ← schema + seed symbols (new databases)
│   ├── sql/migrations/          ← changes for existing databases
│   ├── sql/dashboard-seed.sql   ← optional sample users/orders
│   └── prometheus/prometheus.yml
└── services/
    ├── user-service/            ← auth, JWT, accounts, admin bootstrap
    ├── order-service/           ← orders, funds reservation, market simulator
    ├── matching-engine/         ← order books and trade execution
    └── portfolio-service/       ← trade settlement, holdings, PnL
```

---

## Key Design Decisions

### Matching engine: one thread per symbol, no locks
Each symbol has its own `SymbolEngine` on a virtual thread, fed by a queue. Only that thread
touches the symbol's `OrderBook`, so matching needs no locks.

### Matching rules
- **Price-time priority.** Trades execute at the resting order's price, so the incoming order
  receives any price improvement.
- **MARKET orders are immediate-or-cancel.** They fill against the book and any remainder is
  cancelled. They never rest. They are also capped at ±5% of the reference price
  (`trading.market-protection-pct`), like NSE's market protection.
- **Self-trade prevention.** If an incoming order would match the same user's resting order,
  the resting order is cancelled and matching continues against other users.

### Funds reservation
Available cash and shares are derived, not stored:

```
available cash   = cash − open BUY orders (remaining × price cap) − unsettled BUY trades
available shares = holdings − open SELL orders (remaining qty)  − unsettled SELL trades
```

Placing an order locks the user's row, so concurrent orders are checked one at a time. Fills,
cancels and settlement each update the rows this is computed from in a single transaction,
so there is nothing to release and nothing to leak.

### Transactional outbox
Services never send to Kafka inside a transaction. They write the event to `outbox_events`
in the same transaction as the change, and a relay publishes it after commit, with retries.
Consumers therefore never see an event for a rolled-back change, and a Kafka outage delays
events instead of losing them.

### Idempotent consumers
Delivery is at-least-once, so every consumer tolerates duplicates:
- Portfolio records each trade in `trade_settlements` in the same transaction as the cash change.
- The engine only accepts an order whose row is still `PENDING`, and drops order IDs it has
  already handled.

### Cancels finish in the engine
A cancel sets the order to `CANCELLING`, which keeps its funds reserved. The engine then removes
it from the book and marks it `CANCELLED`, or it ends `FILLED` if a fill got there first. This
way a cancel can never free funds that a queued fill is about to use.

### Order books survive restarts
On startup the engine reloads `PENDING` and `PARTIAL` limit orders from PostgreSQL.

### Admin access
`/api/v1/admin/**` on every service, and the admin WebSocket feed, require a JWT with the `ADMIN`
role. The engine's order book endpoints are public market data.

---

## Dashboards

| Tool | URL |
|---|---|
| Admin dashboard | http://localhost:5173 |
| Kafka UI | http://localhost:8090 |
| Grafana | http://localhost:3000 (admin / admin123) |
| Prometheus | http://localhost:9090 |
| PostgreSQL | localhost:5432 (tradingsim / tradingsim123) |
| Redis | localhost:6379 (password: redis123) |

---

## Roadmap

- **Market data service:** candles (the `candles` table exists) and depth snapshots
- **WebSocket gateway:** streaming prices and fills to traders
- **Trading bots:** strategy bots beyond the built-in market simulator
- **API gateway:** a single entry point with rate limiting
