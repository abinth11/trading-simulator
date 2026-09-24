# Admin Dashboard

TypeScript React admin dashboard for the Trading Simulator project.

## Run

```bash
cd admin-dashboard
npm install
npm run dev
```

## Notes

- Built around the current backend services in this repo:
  - `user-service`
  - `order-service`
  - `matching-engine`
  - `portfolio-service`
- Uses live APIs through Vite proxies in `src/services/api.ts`
- Core frontend architecture:
  - typed domain models in `src/types.ts`
  - shared data hook in `src/hooks/useDashboardData.ts`
  - reusable workstation-style components in `src/components/`
- Main UI sections:
  - Overview
  - Trading blotter and execution mix
  - Matching engine order book
  - User monitor
  - Risk overview
  - System health
