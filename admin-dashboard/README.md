# Admin Dashboard

TypeScript React admin dashboard for the Trading Simulator project.

## Run

```bash
cd admin-dashboard
npm install
npm run dev
```

## Sign in

The dashboard requires an account with the `ADMIN` role. user-service creates one on startup
if it doesn't exist:

- Email: `admin@tradingsim.dev`
- Password: `admin12345`

These are local-dev defaults. Override them with the `ADMIN_EMAIL` / `ADMIN_PASSWORD`
environment variables for user-service, or set them blank to skip creating the account.

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
