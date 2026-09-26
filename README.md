# Google Coffee — Café Companion

An AI-powered café experience built on **three Google Cloud services**:

| Service | What it does here |
|---|---|
| **Gemini on Vertex AI** | "Brew", the conversational AI barista; personalised "picked for you" recommendations; the staff **Room pulse** (sentiment, themes, suggested actions from live feedback) |
| **Firestore** | Menu, guest sessions, orders, feedback, café settings; **real-time listeners** power the live order board and the guest's order tracker |
| **Cloud Run** | Hosts the single container (Spring Boot API + React UI), scales to zero |

![Architecture](docs/architecture.png)

## Features

**Guests** (mobile web, opened from a table QR code such as `/?table=7`)
- Enter a name, table and dietary preferences (vegan, dairy-free, less sugar, no caffeine, prefer hot/cold). No login.
- **Picked for you**: three Gemini recommendations based on time of day, preferences and what they already ordered.
- **Ask Brew**: chat with the AI barista. Every suggestion becomes a card that can be added to the order with one tap.
- **Better waits**: the cart shows "ready in about N min" *before* ordering; after ordering a coffee cup fills up live as the bar works on it, with pickup code, ETA and queue position.
- **Feedback** in two taps (face rating plus optional comment).

**Café team** (`/staff`, PIN protected)
- Live order board: New → Brewing → Ready → Collected (or cancel), late orders highlighted.
- Set how many baristas are on the bar; every guest's ETA updates instantly.
- **Room pulse**: Gemini summarises the last hour of feedback into mood, themes and 2–3 concrete actions.

## How the AI is kept honest (grounding)

- Gemini receives the real menu and must answer in JSON with item **ids**.
- The server drops any id not on the menu and any item that breaks the guest's dietary preferences (`PreferenceRules`). The model can never invent a dish or a price, and can never override "vegan".
- Prices and prep times always come from the server, never the browser.
- Guest text is treated as data inside the prompt; history and message sizes are capped.
- If Gemini is slow or unavailable (timeout 25 s), the app falls back to rule-based picks and ratings-only pulse. The guest flow never breaks.

## How wait times are estimated

No historical data exists yet, so the model is transparent rather than "ML":
- order prep time = slowest item + 1 min per extra item
- queue ETA = sum of prep minutes of orders ahead of you (orders already being made count half) ÷ baristas on the bar

See `EtaCalculator` and its unit tests.

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9+ |
| Node.js | 20.19+ or 22 LTS (Vite 8 requirement) |
| gcloud CLI | recent |

A Google Cloud project with **billing enabled** (credits are fine).

## 2. One-time Google Cloud setup (≈5 min)

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

gcloud services enable aiplatform.googleapis.com firestore.googleapis.com \
  run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# Firestore in Native mode (skip if you already created one)
gcloud firestore databases create --location=asia-south1

# Credentials the app uses when running locally
gcloud auth application-default login
gcloud auth application-default set-quota-project YOUR_PROJECT_ID
```

Then confirm your model is available: Vertex AI → Model Garden → Gemini. The default is `gemini-2.5-flash` in `us-central1`. If your project uses a different model or region, change `GEMINI_MODEL` / `GEMINI_LOCATION` in `.env`.

## 3. Configure

```bash
cp .env.example .env
# edit .env: set GCP_PROJECT_ID, STAFF_PIN and a long STAFF_TOKEN_SECRET (openssl rand -hex 32)
```

## 4. Run locally

### Option A: one command (recommended for a demo)

```bash
./run-local.sh
```

- Guests: http://localhost:8080/?table=7
- Staff: http://localhost:8080/staff (PIN from `.env`, default `2468`)

On first start the app seeds the sample menu into Firestore (collection `menu_items`).

### Option B: dev mode with hot reload (two terminals)

```bash
# Terminal 1: API on :8080
set -a; source .env; set +a
cd backend && mvn spring-boot:run

# Terminal 2: UI on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173/?table=7 and http://localhost:5173/staff.

### Try the full loop in two browser windows

1. Guest window: enter a name, pick "Less sugar", ask Brew for "something cold", add a suggestion, place the order.
2. Staff window: the order appears instantly. Press **Start**, then **Mark ready**. Watch the guest's cup fill and steam.
3. Leave feedback from the guest window (for example "freezing near the window"), then press refresh on **Room pulse**.

### Tests

```bash
cd backend && mvn test
```

Unit tests cover the wait-time maths, dietary grounding rules, order status transitions, staff token signing and lockout, and JSON cleanup of model output. They need no cloud access.

## 5. Deploy to Cloud Run

```bash
./deploy.sh            # uses values from .env; REGION defaults to asia-south1
```

The script enables APIs, creates a least-privilege service account (`roles/datastore.user`, `roles/aiplatform.user`), builds with Cloud Build and deploys. It prints the guest and staff URLs at the end. Tip: turn the guest URL (`…/?table=7`) into a QR code for each table.

---

## API overview

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/menu` | Menu |
| GET | `/api/cafe/status` | Queue busyness and wait for a coffee |
| POST | `/api/sessions` | Start a guest session `{name, table, preferences}` |
| POST | `/api/chat` | Talk to Brew `{sessionId, message, history}` |
| GET | `/api/recommendations?sessionId=` | Personalised picks |
| POST | `/api/orders/estimate` | Wait estimate for a cart |
| POST | `/api/orders` | Place order |
| GET | `/api/orders/{id}/stream?sessionId=` | Live order updates (SSE) |
| POST | `/api/feedback` | Rating and comment |
| POST | `/api/staff/login` | PIN → signed token |
| GET | `/api/staff/stream?token=` | Live order board (SSE) |
| PATCH | `/api/staff/orders/{id}` | Change status (header `X-Staff-Token`) |
| PUT | `/api/staff/settings` | Baristas on bar |
| GET | `/api/staff/pulse` | Room pulse |

## Project layout

```
backend/    Spring Boot 3.5, Java 21
  ai/        GeminiService, BaristaService (grounded chat and picks), PulseService
  live/      LiveOrderHub: Firestore snapshot listener → SSE
  service/   Menu, sessions, orders, feedback, settings, EtaCalculator, PreferenceRules
  security/  StaffAuth: PIN + HMAC-signed expiring token, login lockout
  web/       REST controllers, validation and error handling
frontend/   React 19 + Vite + Tailwind CSS 4
docs/       architecture.svg / .png for the pitch
Dockerfile  multi-stage: UI build → jar build → slim JRE
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Log says "Could not reach Firestore … Serving the bundled sample menu" | Run `gcloud auth application-default login`, check `GCP_PROJECT_ID`, and make sure the Firestore database exists |
| Brew replies "taking a quick breather" | Gemini call failed. Check the log line `Gemini call failed…`: usually the model isn't available in `GEMINI_LOCATION`, the Vertex AI API isn't enabled, or ADC has no quota project |
| `PERMISSION_DENIED` on Cloud Run | Re-run `deploy.sh` (it grants the two roles) and wait a minute for IAM to propagate |
| Cloud Build permission error on first deploy | Grant the Cloud Build/Compute default service account `roles/cloudbuild.builds.builder` in IAM, then retry |
| `npm ci` fails | Use Node 20.19+ or 22 |

## Known limits (deliberate, to keep scope tight)

- One café, English only, pay at counter.
- Staff auth is a single shared PIN. For a real rollout, move secrets to Secret Manager and use per-staff accounts.
- Wait times are a transparent heuristic; with a few weeks of order data they could be replaced by a learned model.
- The menu shown is sample data for a third-wave style café, not any real café's menu.
