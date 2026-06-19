<div align="center">

# 🎮 8Bit Daily Puzzle

**A hyper-local daily puzzle game for IIITB — Wordle first, built so new games are content, not code.**

Mobile-first PWA · server-authoritative · campus + batch leaderboards · runs at **₹0**

</div>

---

## What is this?

A daily puzzle game for IIITB students. You open a link on your phone, log in with your roll number
(which decides your batch) and a username you pick, and play the day's puzzle. Scores feed a
**campus-wide leaderboard** and a **batch-vs-batch war** on the homepage. The 8Bit Magazine editors
load puzzles and hide easter eggs through an admin dashboard.

The hook that makes people actually play — versus ignoring one more Wordle clone — is **hyper-local
content**: campus lore, inside jokes, IIITB references the NYT can never have.

> **For players:** open the link, register in ~30 seconds, play. Add it to your home screen for a
> native-app feel. Keep your streak; carry your batch.
>
> **For recruiters:** this is a from-scratch full-stack system — a Spring Boot **modular monolith**
> with a real **event-driven seam** (the leaderboard updates asynchronously off a domain event,
> ready to extract into a microservice), **server-authoritative anti-cheat**, **Redis sorted-set**
> leaderboards tuned for a midnight traffic spike, JWT auth, an editorial CMS with a two-reviewer
> workflow, a PWA with Web Push, and a ₹0 cloud deployment story (Oracle Always Free + Cloudflare
> Tunnel + Vercel). Designed and load-tested for 1000 concurrent users. See
> [Architecture](#architecture) and [Scalability](#scalability).

This repo implements **Phases 1–5** of the [design document](./8Bit-Puzzle-Build-Doc.md).

---

## Tech stack

| Layer | Tech | Where it runs (prod) | Cost |
|---|---|---|---|
| Frontend | **React 18 + Vite**, PWA (vite-plugin-pwa), plain `fetch`, React Router | Vercel / Cloudflare Pages | ₹0 |
| Backend | **Spring Boot 3.4 (Java 21)**, modular monolith, Spring Security + JWT | Oracle Always Free ARM VM (Docker) | ₹0 |
| Database | **PostgreSQL** (source of truth, JSONB puzzle content) | Neon free / Postgres in Docker | ₹0 |
| Leaderboard / cache | **Redis** (Sorted Sets) | Upstash free / Redis in Docker | ₹0 |
| Async | Spring application events → (Phase 6) Redis Streams / RabbitMQ | in-process | ₹0 |
| Notifications | **Web Push (VAPID)** | browser + backend | ₹0 |
| Edge / TLS | **Cloudflare Tunnel** (no open ports, hidden origin) | Cloudflare | ₹0 |
| CI/CD | GitHub Actions → ghcr.io → SSH deploy | GitHub | ₹0 |

Only unavoidable cost: an optional domain (~₹700–1,200/yr).

---

## Folder structure

```
8Bit/
├── apps/
│   └── web/                     React (Vite) PWA  → Vercel / Cloudflare Pages
│       ├── src/
│       │   ├── pages/           Login, Register, Home, Play, Leaderboard, Profile, User, Admin
│       │   ├── components/      WordleGrid, Keyboard, BatchWarBar, ResultModal, PushToggle, …
│       │   ├── api.js           fetch wrapper (auth header, 401 handling, endpoint helpers)
│       │   ├── auth.jsx         auth context (login/register/logout/token/user)
│       │   └── styles.css       dark retro theme (brand accent #9ED2E6)
│       └── vite.config.js       PWA manifest + offline caching for today's puzzle
│
├── services/
│   └── api/                     Spring Boot modular monolith
│       └── src/main/java/com/eightbit/
│           ├── auth/            roll-number login, JWT, batch extraction
│           ├── game/            puzzle-of-the-day, server-side guess validation, scoring
│           │   └── wordle/      WordleEngine (rules + scoring), WordList
│           ├── leaderboard/     Redis sorted sets, batch-war, PuzzleCompleted listener
│           ├── profile/         streaks, titles, stats
│           ├── admin/           editor CMS: CRUD, review workflow, calendar + gap warnings
│           ├── notification/    Web Push (VAPID) subscriptions + daily reminder job
│           ├── common/          security, config, error handling
│           └── bootstrap/       DataSeeder (demo data on first boot)
│
├── infra/
│   ├── docker-compose.yml       Postgres + Redis + api
│   ├── cloudflared/             Cloudflare Tunnel sample config
│   ├── github-actions/          CI/CD workflow
│   └── loadtest/midnight-spike.js   k6 test ramping to 1000 concurrent users
│
├── 8Bit-Puzzle-Build-Doc.md     full design rationale
└── README.md
```

---

## Architecture

A **modular monolith**: one deployable, internally divided into modules (`auth`, `game`,
`leaderboard`, `profile`, `admin`, `notification`, `common`) that talk only through public services
and **Spring application events** — no reaching into each other's internals.

The key seam: when a player finishes a puzzle, `game` publishes a `PuzzleCompletedEvent`. The
`leaderboard` module consumes it **after the transaction commits, asynchronously**:

```
Player solves ──HTTP──▶ Game module ──"saved!"──▶ Player (instant, no waiting)
                            │
                            │ publish PuzzleCompletedEvent  (after commit, @Async)
                            ▼
                       Leaderboard module ──ZADD──▶ Redis Sorted Sets
```

The player never waits on ranking maths, and at 11:59 PM when everyone submits at once the work
queues harmlessly. Because the seam already exists, turning the leaderboard into its own deployable
over Redis Streams / RabbitMQ (Phase 6) is a small change, not a rewrite — a genuine, honest
event-driven microservices story rather than five JVMs fighting over 12 GB of RAM.

**Anti-cheat is core:** the answer never reaches the browser. Each guess is validated server-side
(`POST /puzzles/{id}/guess` returns only the green/yellow/grey pattern), scoring is computed on the
server from validated moves, `UNIQUE(user_id, puzzle_id)` blocks replays, and a min-participant
floor keeps the batch-war fair.

---

## Run it locally

**Prerequisites:** JDK 21+ · Node 20+ · Docker Desktop. (k6 and cloudflared are optional.)

> ⚠️ On this dev machine, **8080 is taken by Jenkins** and **5432 by a local Supabase**, so the
> examples below use backend port **8088** and the Compose Postgres is published on **5433**. If your
> 8080/5432 are free, you can drop the `SERVER_PORT` override and use 8080.

**1 — Data stores (Docker):**
```bash
cd infra
docker compose up -d postgres redis
```

**2 — Backend (Spring Boot):**

<details open><summary>Windows <code>cmd.exe</code></summary>

```bat
cd /d E:\Projects\8Bit\services\api
set SERVER_PORT=8088
mvn spring-boot:run
```
</details>
<details><summary>PowerShell</summary>

```powershell
cd E:\Projects\8Bit\services\api
$env:SERVER_PORT = 8088
mvn spring-boot:run
```
</details>
<details><summary>macOS/Linux (bash)</summary>

```bash
cd services/api
SERVER_PORT=8088 mvn spring-boot:run
```
</details>

On first boot it auto-creates the schema and seeds 14 days of puzzles, an evergreen failsafe pool,
and an editor account: roll **`IMT2022999`**, username `editor` (you log in by roll number). The
**password is randomly generated and printed once to the backend startup logs** — look for the
`SEEDED EDITOR ACCOUNT` banner. Pin it with `SEED_EDITOR_PASSWORD`, or disable seeding entirely in
prod with `SEED_ENABLED=false`.

**3 — Frontend (React PWA):**
```bash
cd apps/web
copy .env.example .env      # macOS/Linux: cp .env.example .env
# set VITE_API_BASE in .env to match the backend, e.g. http://localhost:8088
npm install
npm run dev                 # → http://localhost:5173
```

Register with any IIITB-style roll number (e.g. `IMT2023045`, `MT2024012`). Log in as the editor to
reach `/admin`.

---

## 📱 Testing on your phone

### A. Same Wi-Fi (LAN) — fastest
1. Find your PC's LAN IPv4: `ipconfig` (e.g. `172.16.138.172`).
2. Set `apps/web/.env` → `VITE_API_BASE=http://<PC-IP>:8088`, then restart `npm run dev`.
3. Open the firewall once (PowerShell as admin):
   ```powershell
   New-NetFirewallRule -DisplayName "8Bit dev" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5173,8088 -Profile Private
   ```
4. On your phone (same Wi-Fi), open `http://<PC-IP>:5173`.

The backend binds all interfaces and CORS defaults to `*`, so no extra config is needed. *If a VPN
like Cloudflare WARP is running on the PC, pause it — it can block LAN access.*

### B. Real HTTPS — for install / offline / push
PWA **install** and **web push** require HTTPS (plain `localhost` is the only exception, and your
phone isn't localhost). Expose both servers with a throwaway tunnel:
```bash
npx cloudflared tunnel --url http://localhost:5173   # frontend
npx cloudflared tunnel --url http://localhost:8088   # backend → put this URL in VITE_API_BASE
```
Now Add-to-Home-Screen, offline-for-today, and notifications work (iOS 16.4+ needs the PWA installed
first — the app shows that hint).

---

## Scalability

**1000 concurrent students is comfortable.** This is a turn-based daily game: each student fires ~7
short requests over a few minutes, so even a full midnight pile-up is a few hundred req/s at peak —
and every request is cheap:

- **Guess validation is an in-memory string compare** (no DB query to decide right/wrong).
- **The leaderboard write is asynchronous** off a domain event — the player's request returns
  immediately; the burst queues instead of blocking.
- **Ranking is Redis sorted sets** (`ZADD` / `ZREVRANGE 0 99` / `ZREVRANK`), all sub-millisecond —
  no `ORDER BY` over Postgres during the spike. Batch-war aggregation is O(1) per finish.
- **`UNIQUE(user_id, puzzle_id)`** bounds writes to ≤1000/day.
- Tomcat is set to 200 worker threads, JVM capped at `-Xmx512m` to fit Oracle's 2 CPU / 12 GB box.

**Verify it yourself** — the included k6 test ramps to 1000 concurrent virtual users:
```bash
k6 run -e BASE=http://localhost:8088 infra/loadtest/midnight-spike.js
# thresholds: <1% errors, p95 < 800ms
```
This breaks only well beyond campus scale (tens of thousands of *simultaneous writers*), which is
exactly when you'd extract the leaderboard service (already seam-ready) and move Postgres to Neon.

---

## Security notes

- **No secrets in the repo — not even placeholders.** `JWT_SECRET` is blank by default (the app
  mints an ephemeral key for dev and logs a warning); the local Postgres uses **trust auth** so there
  is no DB password to commit; the seeded editor password is **randomly generated and printed to the
  logs**. **Set a real `JWT_SECRET` (≥32 bytes) and a real DB password via `infra/.env` in
  production.** Copy `infra/.env.example` → `infra/.env` (gitignored) to supply them.
- Passwords are **BCrypt**-hashed. Auth is stateless JWT; unauthenticated requests get 401, role
  failures get 403 (`/admin/**` requires `ROLE_EDITOR`/`ROLE_ADMIN`).
- **CORS defaults to `*`** — safe here because auth is a Bearer token in a header (no cookies, no
  CSRF surface). Restrict `CORS_ORIGINS` in production if you prefer.
- Demo seeding ships a known editor password; **disable it in prod (`SEED_ENABLED=false`)** or set
  `SEED_EDITOR_PASSWORD`.
- `.gitignore` excludes `.env`, key material (`*.pem/*.key/*.p12/*.jks`), Cloudflare Tunnel creds,
  and build artifacts.

---

## Roadmap / what's left

**Done (Phases 1–5):** Wordle, auth + batch parsing, streaks/profile, Redis leaderboard + batch-war,
editor CMS + review workflow + evergreen failsafe, Web Push plumbing, PWA, ₹0 deploy configs.

**Next:**
- [ ] **Refresh-token rotation** — currently a single 12h access token; add short-lived access +
      rotating refresh before opening campus-wide.
- [ ] **College-email OTP** — `email_verified` is modelled but not wired; needed to stop fake-account
      batch stuffing before any prize round.
- [ ] **Service-worker push display handler** — subscription + server send are done; add the SW
      `push` event handler (vite-plugin-pwa `injectManifest`) to render received notifications.
- [ ] **Phase 6** — extract the leaderboard into its own service over Redis Streams; ship game #2
      (Connections / Pixel Reveal / Cipher — content schema already supports them).
- [ ] **Phase 7** — rate limiting, audit log for suspicious solves, Actuator + Prometheus/Grafana.

---

## License & credits

Built for the **8Bit Magazine Club, IIITB**. See [`8Bit-Puzzle-Build-Doc.md`](./8Bit-Puzzle-Build-Doc.md)
for the full design rationale.
