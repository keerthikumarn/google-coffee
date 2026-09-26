#!/usr/bin/env bash
# Deploys Google Coffee to Cloud Run with a least-privilege service account.
set -euo pipefail
cd "$(dirname "$0")"
if [ -f .env ]; then set -a; source .env; set +a; fi

PROJECT_ID="${GCP_PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-asia-south1}"
SERVICE="${SERVICE:-google-coffee}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-flash}"
GEMINI_LOCATION="${GEMINI_LOCATION:-us-central1}"
FIRESTORE_DATABASE="${FIRESTORE_DATABASE:-(default)}"
: "${STAFF_PIN:?Set STAFF_PIN in .env}"
: "${STAFF_TOKEN_SECRET:?Set STAFF_TOKEN_SECRET in .env (openssl rand -hex 32)}"
SA="google-coffee-run@${PROJECT_ID}.iam.gserviceaccount.com"

echo "▶ Project ${PROJECT_ID}, region ${REGION}"
gcloud config set project "$PROJECT_ID" >/dev/null

echo "▶ Enabling APIs"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  aiplatform.googleapis.com firestore.googleapis.com

echo "▶ Service account"
if ! gcloud iam service-accounts describe "$SA" >/dev/null 2>&1; then
  gcloud iam service-accounts create google-coffee-run --display-name="Google Coffee (Cloud Run)"
fi
for ROLE in roles/datastore.user roles/aiplatform.user; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA}" --role="$ROLE" --condition=None --quiet >/dev/null
done

echo "▶ Building and deploying (Cloud Build, takes a few minutes)"
gcloud run deploy "$SERVICE" \
  --source . \
  --region "$REGION" \
  --service-account "$SA" \
  --allow-unauthenticated \
  --timeout 3600 \
  --memory 1Gi --cpu 1 \
  --min-instances 0 --max-instances 5 \
  --set-env-vars "^|^GCP_PROJECT_ID=${PROJECT_ID}|FIRESTORE_DATABASE=${FIRESTORE_DATABASE}|GEMINI_MODEL=${GEMINI_MODEL}|GEMINI_LOCATION=${GEMINI_LOCATION}|STAFF_PIN=${STAFF_PIN}|STAFF_TOKEN_SECRET=${STAFF_TOKEN_SECRET}"

URL=$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')
echo ""
echo "✔ Guests:  ${URL}/?table=7"
echo "✔ Staff:   ${URL}/staff"
