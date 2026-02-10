# Shopping List

A shared household shopping list application.

## Prerequisites

- Docker and Docker Compose
- JDK 21
- Node.js 20+

## Development Setup

### Start database

```bash
docker-compose up -d
```

### Backend

```bash
cd backend
./gradlew run
```

### Web

```bash
cd web
npm install
npm run dev
```

## Project Structure

- `backend/` - Kotlin/Ktor API server
- `web/` - React/TypeScript web application
- `docs/plans/` - Design and implementation documents
