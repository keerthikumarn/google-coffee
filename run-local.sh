#!/usr/bin/env bash
# Single-process local run: builds the UI into Spring Boot and starts it on http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then set -a; source .env; set +a; fi
: "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID in .env (copy .env.example)}"

echo "▶ Building the UI"
(cd frontend && npm ci --no-audit --no-fund && npm run build)

echo "▶ Bundling UI into the backend"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/

echo "▶ Starting Google Coffee on http://localhost:8080  (staff: http://localhost:8080/staff)"
cd backend && mvn -q spring-boot:run
