# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference

```bash
# Development (starts db, backend, and frontend)
make dev

# Testing
make test              # All tests
make test-backend      # Backend only (./gradlew test)
make test-frontend     # Frontend only (npm run test:run)
make test-e2e          # Playwright E2E tests
make test-e2e-ui       # E2E with Playwright UI

# Single test file
cd backend && ./gradlew test --tests "*AccountServiceTest*"
cd web && npm test -- --run src/store/authSlice.test.ts

# Linting
make lint              # All linters
make lint-backend      # detekt + ktlint
make lint-frontend     # eslint
```

## Architecture

**Backend** (Kotlin/Ktor 3.4.0 at `backend/`, port 8080):
- Entry: `Application.kt` with `EngineMain.main()` for HOCON config loading
- Services: `AccountService`, `HouseholdService`, `ShoppingListService`, `ListItemService`, `ListShareService`
- Routes: `AuthRoutes`, `HouseholdRoutes`, `ShoppingListRoutes`, `SharedAccessRoutes`
- ORM: Exposed with PostgreSQL
- Auth: JWT tokens + optional Google OAuth

**Frontend** (React 19/TypeScript at `web/`, port 5173):
- State: Redux Toolkit with slices in `src/store/` (auth, households, lists)
- Components: Feature-based folders in `src/components/`
- Tests co-located with components (`.test.tsx` files)
- Styling: Tailwind CSS

**Database**: PostgreSQL 16 via Docker Compose (port 5432, creds: shopping/shopping_dev)

## Configuration

Backend config in `backend/src/main/resources/application.conf` (HOCON format). All settings overridable via environment variables:
- `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION_HOURS`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_CALLBACK_URL`

## Testing

**Backend**: Kotest with TestContainers (auto-provisions PostgreSQL)
**Frontend**: Vitest + React Testing Library
**E2E**: Playwright with Docker Compose orchestration (`docker-compose.e2e.yml`)

## Java 25 Notes

Netty requires JVM flags already configured in `build.gradle.kts`:
```
--enable-native-access=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
```

## Code Quality

- Backend: detekt (max 30 lines/method, 120 chars/line) + ktlint
- Frontend: ESLint + Prettier (enforced as errors)
