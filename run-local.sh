#!/usr/bin/env bash
# Runs Google Coffee on http://localhost:8080 as a single process.
# Profile "local" (default): PostgreSQL (Docker) + Ollama (native). Profile "gcp": Firestore + Gemini.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then set -a; source .env; set +a; fi
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"
export SPRING_PROFILES_ACTIVE="$PROFILE"
echo "▶ Profile: ${PROFILE}"

if [ "$PROFILE" = "local" ]; then
  if [ "${USE_DOCKER_POSTGRES:-true}" = "true" ]; then
    command -v docker >/dev/null || { echo "Docker not found. Install Docker Desktop, or set USE_DOCKER_POSTGRES=false and point DATABASE_URL at your own Postgres."; exit 1; }
    echo "▶ Starting PostgreSQL (Docker)"
    docker compose up -d --wait postgres
  fi
  OLLAMA="${OLLAMA_BASE_URL:-http://localhost:11434}"
  if curl -sf "${OLLAMA}/api/tags" >/dev/null; then
    echo "▶ Ollama reachable at ${OLLAMA} (model: ${OLLAMA_MODEL:-llama3.2})"
  else
    echo "⚠ Ollama not reachable at ${OLLAMA}. Start it (open the Ollama app or run 'ollama serve'). AI features will fall back until then."
  fi
else
  : "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID in .env for the gcp profile}"
fi

echo "▶ Building the UI"
(cd frontend && npm ci --no-audit --no-fund && npm run build)

echo "▶ Bundling UI into the backend"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/

echo "▶ Starting Google Coffee on http://localhost:8080  (staff: http://localhost:8080/staff)"
cd backend && mvn -q spring-boot:run
