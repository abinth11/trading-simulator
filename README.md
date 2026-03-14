# Trading Simulator

A production-grade stock exchange simulator built with Java 21, Spring Boot 3, PostgreSQL, Redis and Kafka.

---

## Architecture

```
Client (React)
    │
Nginx API Gateway         ← JWT auth, rate limiting, routing
    │
┌───┴───────────────────────────────────────┐
│  Core Services                            │
│  ├── User Service       :8081             │
│  ├── Order Service      :8082             │
│  ├── Portfolio Service  :8084             │
│  └── Market Data Svc    :8085             │
└───────────────┬───────────────────────────┘
                │
        Matching Engine   :8083
        (one virtual thread per symbol)
                │
          Kafka Event Bus
          ├── Portfolio Updater
          ├── Market Data Updater
          └── Trade Logger
                │
        WebSocket Gateway :8086 → Client
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads via Project Loom) |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache / Pub-Sub | Redis 7 |
| Message Bus | Kafka (Confluent) |
| Auth | JWT (JJWT 0.12) |
| Build | Maven (multi-module) |
| Observability | Prometheus + Grafana |
| Containers | Docker Compose |

---

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts: PostgreSQL, Redis, Kafka, Kafka UI, Prometheus, Grafana.

Wait for health checks to pass:
```bash
docker compose ps
```

### 2. Run the User Service

```bash
cd services/user-service
mvn spring-boot:run
```

Service starts on `http://localhost:8081`

### 3. Test the User Service

```bash
# Register
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"trader@example.com","username":"trader1","password":"password123"}'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"trader@example.com","password":"password123"}'

# Get profile (replace TOKEN)
curl http://localhost:8081/api/v1/auth/me \
  -H "Authorization: Bearer TOKEN"
```

### 4. Run tests

```bash
# All tests
mvn test

# Single service
cd services/matching-engine && mvn test
```

---

## Project Structure

```
trading-simulator/
├── pom.xml                          ← Parent POM (all shared deps)
├── docker-compose.yml               ← Full local infra
│
├── infra/
│   ├── sql/init.sql                 ← DB schema + seed data
│   └── prometheus/prometheus.yml
│
└── services/
    ├── user-service/                ← Auth, JWT, user profiles
    ├── order-service/               ← Order lifecycle, validation
    ├── matching-engine/             ← Core: order book, trade execution
    ├── portfolio-service/           ← Holdings, PnL tracking
    ├── market-data-service/         ← Candles, order book depth
    ├── websocket-gateway/           ← Live streaming to clients
    └── trading-bots/                ← Random, Momentum, Mean Reversion bots
```

---

## Build Order (recommended)

1. **User Service** — auth foundation, no dependencies
2. **Matching Engine** — pure Java, unit test heavily first
3. **Order Service** — connects to matching engine
4. **Portfolio Service** — consumes TradeExecuted events
5. **Market Data Service** — candles, order book feed
6. **WebSocket Gateway** — live streaming
7. **Trading Bots** — market simulation

---

## Key Design Decisions

### Matching Engine — no locks
One virtual thread per symbol. The `OrderBook` class is single-threaded by design.
Race conditions are impossible because only one thread ever accesses a given symbol's book.

### Event-driven consumers
All portfolio and market data updates happen by consuming Kafka events, not via direct service calls.
This keeps services loosely coupled and allows replay on restart.

### Order book on restart
On startup, the matching engine queries `pending_orders` from PostgreSQL and rebuilds
the in-memory order books. No state is lost on crash.

### Virtual threads (Java 21)
All services use `spring.threads.virtual.enabled=true`.
The matching engine runs one virtual thread per symbol — lightweight, no thread pool sizing needed.

---

## Dashboards

| Service | URL |
|---|---|
| Kafka UI | http://localhost:8090 |
| Grafana | http://localhost:3000 (admin/admin123) |
| Prometheus | http://localhost:9090 |
| PostgreSQL | localhost:5432 (tradingsim/tradingsim123) |
| Redis | localhost:6379 (password: redis123) |
