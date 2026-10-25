# Thin entry points that run the backend and the frontend from the repository root.
# They call the same commands the README documents; nothing here is special.
# Needs: JDK 21, Node.js 22.12+, and (for the 35 PostgreSQL tests) Docker. Maven is downloaded by ./mvnw.

.DEFAULT_GOAL := help
.PHONY: help install test test-backend test-frontend lint lint-backend lint-frontend format build build-backend build-frontend check run

help: ## List the available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  make %-14s %s\n", $$1, $$2}'

install: ## Install the frontend dependencies exactly as package-lock.json says
	npm --prefix frontend ci --no-audit --no-fund

test: test-backend test-frontend ## Run the backend and the frontend tests

test-backend: ## Run the backend tests (Docker-dependent ones are skipped, with a warning, if Docker is missing)
	cd backend && ./mvnw -B test

test-frontend: install ## Run the frontend tests
	npm --prefix frontend test

lint: lint-backend lint-frontend ## Check formatting and lint on both stacks

lint-backend: ## Spotless check on the Java code
	cd backend && ./mvnw -B spotless:check

lint-frontend: install ## ESLint and Prettier check on the React code
	npm --prefix frontend run lint
	npm --prefix frontend run format:check

format: ## Fix formatting on both stacks
	cd backend && ./mvnw -B spotless:apply
	npm --prefix frontend run lint:fix
	npm --prefix frontend run format

build: build-backend build-frontend ## Build the jar and the production UI bundle

build-backend: ## Build backend/target/google-coffee.jar (tests are run by "make test")
	cd backend && ./mvnw -B -DskipTests package

build-frontend: install ## Build the production UI bundle into frontend/dist
	npm --prefix frontend run build

check: lint test build ## Everything CI runs: lint, tests, build

run: ## Start the app locally (Postgres in Docker, Ollama on this machine); see the README
	./run-local.sh
