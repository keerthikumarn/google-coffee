#!/usr/bin/env bash
# One-time setup for the Cloud Build CI/CD pipeline. Run AFTER a first successful ./deploy.sh.
set -euo pipefail
cd "$(dirname "$0")"
if [ -f .env ]; then set -a; source .env; set +a; fi

PROJECT_ID="${GCP_PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-asia-south1}"
SERVICE="${SERVICE:-google-coffee}"
REPO="google-coffee"
RUN_SA="google-coffee-run@${PROJECT_ID}.iam.gserviceaccount.com"
BUILD_SA="google-coffee-build@${PROJECT_ID}.iam.gserviceaccount.com"
: "${STAFF_PIN:?Set STAFF_PIN in .env}"
: "${STAFF_TOKEN_SECRET:?Set STAFF_TOKEN_SECRET in .env}"

gcloud config set project "$PROJECT_ID" >/dev/null

echo "▶ Enabling APIs"
gcloud services enable cloudbuild.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com

echo "▶ Artifact Registry repository"
if ! gcloud artifacts repositories describe "$REPO" --location "$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$REPO" --repository-format=docker --location="$REGION" \
    --description="Google Coffee images"
fi

echo "▶ Secrets (staff PIN and token secret)"
upsert_secret() {
  local name="$1" value="$2"
  if gcloud secrets describe "$name" >/dev/null 2>&1; then
    printf %s "$value" | gcloud secrets versions add "$name" --data-file=- >/dev/null
  else
    printf %s "$value" | gcloud secrets create "$name" --replication-policy=automatic --data-file=- >/dev/null
  fi
  gcloud secrets add-iam-policy-binding "$name" --member="serviceAccount:${RUN_SA}" \
    --role=roles/secretmanager.secretAccessor --condition=None --quiet >/dev/null
}
upsert_secret staff-pin "$STAFF_PIN"
upsert_secret staff-token-secret "$STAFF_TOKEN_SECRET"

echo "▶ Build service account (least privilege)"
if ! gcloud iam service-accounts describe "$BUILD_SA" >/dev/null 2>&1; then
  gcloud iam service-accounts create google-coffee-build --display-name="Google Coffee CI/CD"
fi
for ROLE in roles/run.developer roles/artifactregistry.writer roles/logging.logWriter; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:${BUILD_SA}" \
    --role="$ROLE" --condition=None --quiet >/dev/null
done
# Allow the pipeline to deploy Cloud Run revisions that run as the runtime service account.
gcloud iam service-accounts add-iam-policy-binding "$RUN_SA" \
  --member="serviceAccount:${BUILD_SA}" --role=roles/iam.serviceAccountUser --quiet >/dev/null

echo "▶ Moving the live service from plain env vars to Secret Manager"
if gcloud run services describe "$SERVICE" --region "$REGION" >/dev/null 2>&1; then
  gcloud run services update "$SERVICE" --region "$REGION" \
    --remove-env-vars=STAFF_PIN,STAFF_TOKEN_SECRET \
    --update-secrets=STAFF_PIN=staff-pin:latest,STAFF_TOKEN_SECRET=staff-token-secret:latest
fi

echo ""
echo "✔ Done. Next: connect your GitHub repo and create the trigger (README, section CI/CD)."
echo "  Trigger service account: ${BUILD_SA}"
