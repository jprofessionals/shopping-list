.PHONY: dev backend frontend db test test-backend test-frontend test-e2e test-e2e-ui test-e2e-headed lint build clean install help test-mobile build-mobile lint-mobile

# Start everything (database + backend + frontend)
dev: db
	@echo "Starting backend and frontend..."
	@make -j2 backend frontend

# Start only the backend
backend:
	cd backend && ./gradlew run

# Start only the frontend
frontend:
	cd web && npm run dev

# Start the database
db:
	docker compose up -d

# Stop the database
db-stop:
	docker compose down

# Run all tests
test: test-backend test-frontend

# Run backend tests
test-backend:
	cd backend && ./gradlew test

# Run frontend tests
test-frontend:
	cd web && npm run test:run

# Run linting
lint: lint-backend lint-frontend

lint-backend:
	cd backend && ./gradlew detekt

lint-frontend:
	cd web && npm run lint

# Build for production
build: build-backend build-frontend

build-backend:
	cd backend && ./gradlew build

build-frontend:
	cd web && npm run build

# Install dependencies
install:
	cd web && npm install

# Clean build artifacts
clean:
	cd backend && ./gradlew clean
	cd web && rm -rf dist node_modules/.vite

# Help
help:
	@echo "Available targets:"
	@echo "  dev            - Start database, backend, and frontend"
	@echo "  backend        - Start only the backend"
	@echo "  frontend       - Start only the frontend"
	@echo "  db             - Start the database"
	@echo "  db-stop        - Stop the database"
	@echo "  test           - Run all tests"
	@echo "  test-backend   - Run backend tests"
	@echo "  test-frontend  - Run frontend tests"
	@echo "  lint           - Run all linters"
	@echo "  build          - Build for production"
	@echo "  install        - Install frontend dependencies"
	@echo "  clean          - Clean build artifacts"

# Mobile
test-mobile:
	cd mobile && ./gradlew :shared:jvmTest

build-mobile:
	cd mobile && ./gradlew :androidApp:assembleDebug

lint-mobile:
	cd mobile && ./gradlew lint

# Run E2E tests (requires dev environment running)
test-e2e:
	cd web && npm run test:e2e

# Run E2E tests with UI
test-e2e-ui:
	cd web && npm run test:e2e:ui

# Run E2E tests in headed mode
test-e2e-headed:
	cd web && npm run test:e2e:headed
