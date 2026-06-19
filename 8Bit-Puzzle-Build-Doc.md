# 8Bit Daily Puzzle — Build Document

**Owner:** 8Bit Magazine Club, IIITB
**Your role:** Web app, backend (Spring Boot), microservices, cloud
**Constraint:** Keep running cost at ₹0 wherever possible, and flag the moment a cost becomes unavoidable
**First game:** Wordle. Designed so adding more games later is content work, not a rebuild.

This is a build guide, not a pitch deck. It tells you what to build, in what order, where it runs, what it costs, and how the editor team feeds it. Read the "Reality checks" box first because two of your assumptions need adjusting before you write any code.

---

## 0. Reality checks (read this before anything else)

These are the things that will bite you if nobody says them out loud.

**1. Vercel cannot host Spring Boot.** Vercel runs static frontends and short Node/Python serverless functions. It does not run a long-lived Java/JVM server. So Vercel hosts your **React frontend only**. Your Spring Boot backend has to live somewhere that runs containers. That is fine and normal, you just split the two.

**2. Vercel's free (Hobby) plan is personal, non-commercial only.** A free club game with no money changing hands sits inside that. The moment you take sponsorship money, run paid canteen promos through the app, or otherwise make revenue, you are technically in "commercial" territory and Vercel expects you to move to Pro ($20/month). Easy fix if that day comes: host the frontend on **Cloudflare Pages** instead (also free, commercial use allowed, more generous bandwidth). Keep this in your back pocket.

**3. Oracle's "always free" allowance shrank.** As of 15 June 2026 the Always Free ARM (Ampere A1) machine dropped from 4 CPU / 24 GB RAM to **2 CPU / 12 GB RAM**. Still plenty for this project, but it means you should not try to run five separate Java services on it. 12 GB disappears fast when every JVM wants 300 to 500 MB just to idle.

**4. Microservices are a learning goal, not a day-one need.** A campus game with a few thousand players is a textbook case for a **modular monolith**. You will build one Spring Boot app with clean internal modules, get it live, then *extract* one service (the leaderboard) into its own deployable so you have a real, honest event-driven system to talk about in interviews. This respects both the 12 GB ceiling and the resume goal. More on this in Section 4.

**The only unavoidable cost in the whole project is a domain name**, roughly ₹700 to ₹1,200 a year for a `.in` or `.com`. And even that is optional at the start (you can run on the free Vercel subdomain plus a Cloudflare tunnel hostname). Everything else below is ₹0.

---

## 1. What we are building

A daily puzzle game for IIITB students, mobile-first, opens in a browser, no app install required (though it can be "added to home screen" as a PWA). One puzzle per game per day. Players log in with their roll number (which decides their batch) and a username they pick. Scores feed individual and batch leaderboards. The 8Bit editors load puzzles and hide easter eggs through an admin dashboard.

The whole point that makes people actually play, versus ignoring it like one more NYT clone, is **hyper-local content**: campus lore, inside jokes, IIITB references the New York Times can never have. The tech below is the easy part. The content engine (Section 11) is what keeps it alive.

### Success criteria for v1
- A student can open a link, register, and play Wordle on their phone in under 30 seconds.
- The server, not the browser, decides whether a guess is right. No cheating by reading the page source.
- A daily leaderboard ranks players campus-wide and by batch.
- Editors can schedule a month of puzzles without touching code.
- It costs ₹0 to run at IIITB scale.

---

## 2. Game catalogue (what 8Bit can ship over time)

The architecture treats a "game" as a **type** with: a JSON content schema, a server-side validator/scorer, and a frontend renderer. Adding a game = add those three things. The auth, leaderboard, profile, scheduling, and notification systems are shared and never change. Build that seam once and you can ship a new game in a weekend.

**Launch now**
- **Wordle.** Five-letter daily word, six guesses, green/yellow/grey feedback. Known, low-friction, viral share grid. Good first build because the rules are simple and the scoring is clean.

**Strong next three (pick based on editor appetite)**
- **Connections.** Sixteen tiles, sort into four hidden groups. This is the one that turns into campus lore gold: "Nicknames for the night mess," "Things that break during endsem," "Profs who start class 10 minutes late." Highest retention of the NYT-style games because the categories are where you smuggle the jokes in.
- **Pixel Reveal (very on-brand for "8Bit").** A campus photo, a spot, a landmark, a (consenting) prof, starts fully pixelated / 8-bit and de-pixelates step by step. Fewer steps to guess = more points. Hyper-local, instantly recognisable, and the name fits the club. Easter-egg heaven.
- **Cipher / Decode.** A short message encoded in Caesar shift, binary, hex, or ASCII. This is catnip for an IIITB crowd and literally plays on "8 bit." You can theme a weekly "decode the 8-bit value" puzzle.

**Backlog (add when you have content runway to spare)**
- Mini crossword (campus-clued)
- Anagram / word ladder
- Daily trivia (campus + general)
- Emoji puzzle (guess the club / course / event from emojis)
- Mess-menu "higher or lower" (silly, sticky)
- Logic grid (weekend hard mode)

**Difficulty rhythm.** Copy the NYT habit: easy on Monday, hardest on Friday and the weekend. People like a predictable curve. Bake a `difficulty` field into every puzzle so the calendar enforces it.

---

## 3. Tech stack and where each piece runs

| Layer | Choice | Where it runs | Cost | Notes |
|---|---|---|---|---|
| Frontend | React (Vite) PWA | **Vercel Hobby** (or Cloudflare Pages) | ₹0 | Mobile-first, installable, offline cache for today's puzzle |
| Backend | Spring Boot (Java 21) | **Oracle Cloud Always Free** ARM VM, in Docker | ₹0 | Modular monolith first, extract later |
| Reverse proxy / TLS / expose | **Cloudflare Tunnel** + Cloudflare DNS | Cloudflare | ₹0 | No public IP or cert juggling needed; hides the origin |
| Primary DB | PostgreSQL | **Neon** free (or Postgres in Docker on the VM) | ₹0 | Neon keeps the VM lean; self-host if you want full control |
| Cache + leaderboard | Redis (Sorted Sets) | **Upstash** free (or Redis in Docker on the VM) | ₹0 | Sorted Sets are the whole reason leaderboards stay fast |
| Async messaging (Phase 6) | Redis Streams (simple) or RabbitMQ | On the VM | ₹0 | Skip Kafka, it is too heavy for 12 GB. See Section 4. |
| Push notifications | Web Push (VAPID) | Browser + your backend | ₹0 | The only genuinely free push channel for a PWA |
| Email (OTP, optional) | Brevo or Resend free tier | SaaS | ₹0 | Only if you verify college email. Low volume. |
| Container registry | GitHub Container Registry (ghcr.io) | GitHub | ₹0 | Free for public images |
| CI/CD | GitHub Actions | GitHub | ₹0 | Build image, push to ghcr, deploy to VM over SSH |
| Monitoring (optional) | Spring Actuator + Prometheus + Grafana | On the VM, or Grafana Cloud free | ₹0 | Add in Phase 7, not before |
| Domain | `.in` / `.com` | Registrar | **~₹700–1,200/yr** | The one real cost. Optional at first. |

**Why Oracle and not Render/Railway/Fly for the backend:**
- **Render free** spins your service down after 15 minutes idle, so the first player after a quiet spell waits 30 to 60 seconds for a cold JVM. Its free Postgres also expires and gets deleted after about a month. Fine for a throwaway demo, wrong for a daily-habit product.
- **Railway** is really a trial ($5 once, then ~$1/month of credit). It will run dry.
- **Fly.io** free VMs are 256 MB, which a Spring Boot app barely starts in.
- **Oracle Always Free** is a real always-on VM (2 CPU / 12 GB) that never expires. The catch: signup needs a card (you are not charged on a free-only account), and the free ARM capacity is sometimes "out of capacity" in a region, so you may need to retry or pick a region with stock (Mumbai or Hyderabad or Singapore for you). Worth the one-time hassle.
- **Honourable mention: Google Cloud Run.** Scales to zero (₹0 when idle), generous free request tier, handles the midnight burst well. Trade-off is Spring Boot cold-start latency. Good fallback if Oracle capacity fights you. If you go this route, look at Spring Boot AOT / native image to cut cold starts.

---

## 4. Architecture: monolith now, microservice seam later

Do not start with five services. Start with **one Spring Boot app, cleanly modularised**, then split off exactly one service so you can honestly say "event-driven microservices" without setting 12 GB of RAM on fire.

### 4a. Phase 1 to 5: modular monolith

One deployable, internally divided into modules with no cross-imports between their internals (enforce it with Spring Modulith or just package discipline):

```
com.eightbit
├── auth          // roll-number login, JWT, batch extraction
├── game          // puzzle of the day, server-side guess validation, scoring
├── leaderboard   // Redis sorted sets, batch aggregation
├── profile       // streaks, titles, stats, easter eggs found
├── admin         // editor CMS endpoints, scheduling, review workflow
├── notification  // web push subscriptions and sends
└── common        // security, error handling, config
```

The `game` module never calls leaderboard code directly. When a player finishes, `game` publishes an **application event** (`PuzzleCompleted`). The `leaderboard` module listens and updates Redis. This is an in-process event today, but because the seam already exists, turning it into a cross-service message later is a small change, not a rewrite.

### 4b. Phase 6: extract the leaderboard service

Now split `leaderboard` into its own Spring Boot app. The two talk over **Redis Streams** (you already run Redis, zero new infrastructure) or **RabbitMQ** (heavier, but a "real" broker if you want that on the CV).

```
Player solves  ──HTTP──▶  Game Service  ──"saved!"──▶  Player (instant, no waiting)
                              │
                              │ publish PuzzleCompleted  (Redis Stream / RabbitMQ)
                              ▼
                        Leaderboard Service  ──ZADD──▶  Redis Sorted Sets
```

Why this matters and why it is the *right* amount of microservice:
- The player never waits on leaderboard maths. The game replies "saved," the ranking happens behind the scenes.
- At 11:59 PM when everyone submits at once, messages queue safely instead of overloading anything. If the leaderboard service is briefly swamped, the stream buffers and it catches up. Nothing is lost, nothing crashes.
- You now genuinely have an "event-driven microservices architecture" to describe, and it fits in 12 GB because it is two tuned JVMs (cap each with `-Xmx384m`), not five.

**Skip Kafka.** Kafka plus Zookeeper/KRaft is a memory hog that makes no sense for campus traffic on a 12 GB box. Redis Streams gives you the same "durable queue, consumer catches up" behaviour for free. If an interviewer specifically wants Kafka talk, you can say you chose Redis Streams deliberately for the resource envelope and explain when you'd reach for Kafka instead. That answer is *more* impressive than cargo-culting Kafka into a student project.

### 4c. The midnight burst, concretely

The whole load profile is "almost nothing, then everyone at once when the puzzle drops, then almost nothing." Two things absorb it:
1. **Server-authoritative but cheap validation.** Validating a Wordle guess is a string compare. It is the leaderboard write that you make async.
2. **Redis Sorted Sets for ranking.** `ZADD` to insert, `ZREVRANGE` to read top 100, `ZREVRANK` for "your rank." All sub-millisecond. You never run a heavy `ORDER BY` over Postgres during the spike.

---

## 5. Data model (PostgreSQL)

Keep Postgres as the source of truth. Redis is a fast derived view you can always rebuild from Postgres if it is wiped.

```sql
-- Players
CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  roll_number   VARCHAR(20) UNIQUE NOT NULL,   -- identity, drives batch
  username      VARCHAR(30) UNIQUE NOT NULL,    -- public pseudonym
  password_hash VARCHAR(100) NOT NULL,          -- BCrypt
  batch_year    INT NOT NULL,                   -- parsed from roll_number
  program       VARCHAR(10),                    -- e.g. iMTech, MTech (parsed)
  email_verified BOOLEAN DEFAULT FALSE,
  created_at    TIMESTAMPTZ DEFAULT now()
);

-- One row per game type
CREATE TABLE game_types (
  code        VARCHAR(20) PRIMARY KEY,          -- 'wordle', 'connections', ...
  display_name VARCHAR(50),
  active      BOOLEAN DEFAULT TRUE
);

-- Scheduled puzzles. content is per-type JSON validated in code.
CREATE TABLE puzzles (
  id           BIGSERIAL PRIMARY KEY,
  game_type    VARCHAR(20) REFERENCES game_types(code),
  publish_date DATE,                            -- NULL = evergreen failsafe pool
  difficulty   SMALLINT,                        -- 1 (Mon) .. 5 (Fri/weekend)
  content      JSONB NOT NULL,                  -- answer, tiles, etc. NEVER sent raw to client
  easter_eggs  JSONB,                           -- trigger conditions + payloads
  status       VARCHAR(10) DEFAULT 'draft',     -- draft | in_review | scheduled | published
  author_id    BIGINT REFERENCES users(id),
  reviewer_id  BIGINT REFERENCES users(id),
  created_at   TIMESTAMPTZ DEFAULT now(),
  UNIQUE (game_type, publish_date)              -- one puzzle per game per day
);

-- One attempt per user per puzzle
CREATE TABLE attempts (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT REFERENCES users(id),
  puzzle_id     BIGINT REFERENCES puzzles(id),
  guesses       JSONB,                          -- the moves made
  solved        BOOLEAN,
  score         INT,
  completion_ms INT,                            -- time taken, sanity-checked
  eggs_found    JSONB,
  finished_at   TIMESTAMPTZ,
  UNIQUE (user_id, puzzle_id)                   -- hard stop on replaying for a better score
);

-- Streaks and unlocked titles, kept here so they survive a Redis wipe
CREATE TABLE user_stats (
  user_id        BIGINT PRIMARY KEY REFERENCES users(id),
  current_streak INT DEFAULT 0,
  best_streak    INT DEFAULT 0,
  total_played   INT DEFAULT 0,
  total_solved   INT DEFAULT 0,
  titles         JSONB DEFAULT '[]'
);

-- Web push subscriptions
CREATE TABLE push_subscriptions (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT REFERENCES users(id),
  endpoint    TEXT,
  p256dh      TEXT,
  auth        TEXT,
  created_at  TIMESTAMPTZ DEFAULT now()
);
```

The `content` column being JSONB is what makes new games cheap. Wordle stores `{"answer":"PIXEL"}`. Connections stores groups. Pixel Reveal stores an image id and reveal steps. Same table, different shape, validated per type in code.

---

## 6. Login and identity (roll number + pseudonym)

The flow you described works well, with one security caveat: **roll number alone is not a credential**, because anyone who knows a classmate's roll number could log in as them. Add a password (and optionally email OTP) so batch competition can't be sabotaged.

### Registration
1. User enters **roll number**, picks a **username** (pseudonym, need not be real name), sets a **password**.
2. Backend validates the roll-number format, then **parses batch year and program from it**.
3. (Recommended) Send a one-time code to their `@iiitb.ac.in` email and require it before the account counts toward batch scores. This is your main defence against one person making 50 fake accounts to pump their batch.
4. Store BCrypt password hash. Issue JWT.

### Parsing the roll number
IIITB roll numbers encode the batch year and program (for example an iMTech roll like `IMT2023045` implies batch 2023, program iMTech; MTech and MSc and PhD have their own prefixes). **Confirm the exact current scheme with the institute before you hardcode it**, then drive it from config so you can fix it without a redeploy:

```yaml
rollnumber:
  pattern: "^(IMT|MT|MS|PH|DT)(\\d{4})(\\d{3,4})$"
  program-map:
    IMT: iMTech
    MT: MTech
    MS: MSc
    PH: PhD
```

Extract group 2 as `batch_year`, group 1 as `program`. If a roll number does not match, reject registration with a clear message rather than guessing.

### Auth mechanics
- **Spring Security** with stateless JWT. Short-lived access token (15 min) plus a rotating refresh token (httpOnly cookie, stored server-side so you can revoke).
- Roll number is the **identity** (and the batch key). Username is the **public display name**. Never show roll numbers on leaderboards.
- Admin/editor accounts get a role claim (`ROLE_EDITOR`, `ROLE_ADMIN`) that gates the `/admin/**` routes.

---

## 7. Gameplay and UX (Wordle, but the patterns generalise)

Mobile-first, thumb-reachable, fast. The screens:

**Onboarding (once):** roll number + username + password, batch auto-detected and shown ("Welcome, Batch '23"), a 15-second how-to overlay.

**Game screen:** the grid, an on-screen keyboard, tile flip animation, light haptic feedback on submit. Works offline for the day's puzzle once loaded (PWA cache), so a dropout in the mess Wi-Fi does not lose progress.

**Result screen:** score, streak status ("🔥 7 days"), what you contributed to your batch today, and the **share button**. The share copies an emoji grid (the green/yellow/grey squares) plus a line like "8Bit • IIITB • 4/6." That share grid is your single biggest free growth lever; it is why Wordle spread. Make it one tap.

### Server-authoritative guessing (this is the anti-cheat core)
Do **not** ship the answer to the browser. Two options:

- **Per-guess validation (recommended for a competition).** Client sends each guess to `POST /puzzles/{id}/guess`; server returns the colour pattern (greens/yellows/greys) and never reveals the word until solved or out of guesses. The answer stays on the server the whole time. Slightly chattier, but nobody can read the answer from the page or the network tab.
- **Hashed answer (lighter, less strict).** Ship a salted hash; client checks its own guess. Faster and offline-friendly, but a determined student can brute-force a 5-letter space. Acceptable for casual play, not for a leaderboard people care about.

Go per-guess for the ranked daily. You can still cache the in-progress state so a refresh does not wipe their board.

### Scoring (server-side, always)
A simple, hard-to-game model:
- Base points for solving.
- Bonus for fewer guesses (solve in 2 beats solve in 6).
- Small time bonus, **capped**, so a fast human is rewarded but a bot solving in 80 ms gains nothing extra.
- Streak multiplier applied server-side.

Compute this on the server from the validated guesses. The client never submits a score, only its moves.

---

## 8. Leaderboard (Redis Sorted Sets + the batch-war hero)

Maintain several sorted sets in parallel. They cost almost nothing to keep in sync because each finished puzzle is just a few `ZADD`s.

```
lb:wordle:2026-06-19:campus            (member = userId, score = points)
lb:wordle:2026-06-19:batch:2023
lb:wordle:alltime:campus
```

Reads: `ZREVRANGE ... 0 99 WITHSCORES` for the top 100, `ZREVRANK` for "you are #214." Milliseconds, even mid-spike.

**The homepage hero is the batch war, not the individual board.** A tug-of-war bar ("Batch '23 vs Batch '24, today") drives the peer pressure that gets non-puzzle-people to play so their batch doesn't lose.

**Fairness gotcha you must handle:** if batch score = sum of member scores, the biggest batch always wins and the war is boring. **Normalise.** Rank batches by *average score per participating player*, or by participation rate, or a blend. Decide this early because it changes how you aggregate. A good default: `batch_score = average points among that batch's players who played today`, with a minimum-participants floor so a single tryhard can't carry an empty batch.

Leaderboard tabs in the UI: **Today / All-time**, filter **Campus / My Batch**, with the batch-war bar pinned on top. Always highlight the player's own row.

---

## 9. Profile, streaks, and titles

The profile is where retention psychology lives.

- **Streak** (current + best), the classic daily hook.
- **Stats:** win %, guess distribution, average solve time, easter eggs found.
- **Titles / badges,** awarded server-side from playstyle, shown next to the pseudonym on leaderboards. Examples: "Library Ghost" (solves regularly after 2 AM), "Panic Submitter" (fast but error-prone), "First Blood" (first solver of the day), "Lore Keeper" (found a rare easter egg). These create a meta-game of chasing specific titles, which NYT can't replicate because they don't know your campus.

Keep streaks and titles in Postgres (`user_stats`), not only Redis, so a cache wipe never erases someone's 60-day streak. That would be the one bug that makes people quit forever.

---

## 10. Notifications

The realistic free channel for a PWA is **Web Push (VAPID)**. No third-party cost. Modern Android and desktop support it, and iOS supports web push for **installed** PWAs (iOS 16.4+), so prompt iPhone users to "Add to Home Screen" first.

What to send (sparingly, this is how you lose people):
- "Today's 8Bit puzzle is live" (once, at a friendly hour).
- Streak-saver nudge if they haven't played and their streak is about to break.
- "Your batch just took the lead" / "you slipped to #X" (optional, opt-in).

**Timing:** even if the puzzle drops at midnight, do not push at midnight. Pick a civilised hour (7 to 9 AM) and respect quiet hours. Store subscriptions in `push_subscriptions`; send with a VAPID library from the notification module/service. A weekly email digest via Brevo/Resend free tier is a nice optional extra.

---

## 11. The editor side: CMS, workflow, and how far ahead to stay

This is the part that decides whether the game survives past week three. The tech is a CRUD dashboard; the discipline is a content pipeline.

### 11a. Editor dashboard (admin CMS)
A separate authenticated area (`/admin`, gated by `ROLE_EDITOR`/`ROLE_ADMIN`). It needs:
- **Create / edit** a puzzle for any game type, with a form that matches that type (word entry for Wordle, four-group builder for Connections, image upload + reveal steps for Pixel Reveal).
- **Live preview / play-test** the puzzle exactly as a student will see it, before it goes out.
- **Validation on save:** Wordle word is a real, allowed word and exactly five letters; Connections groups are unambiguous; no banned/offensive content; colourblind-safe (lean on symbols/labels, not colour alone).
- **Easter-egg configurator:** set a trigger (e.g. "these four specific wrong tiles guessed together") and the payload (a modal with campus lore). Stored in `puzzles.easter_eggs`.
- **Calendar view** of the schedule with **gap warnings** ("no Connections puzzle for 9 July").
- **Schedule** by setting `publish_date`; backend serves `WHERE publish_date = CURRENT_DATE AND status = 'published'`.

### 11b. Review workflow (non-negotiable)
Every puzzle goes `draft → in_review → scheduled`. **A second editor must approve before it can be scheduled.** This catches the two failure modes that embarrass you publicly: an ambiguous puzzle with two valid answers, and a reference that is offensive or excludes freshers. The `author_id`/`reviewer_id` columns enforce "not the same person."

### 11c. How far ahead must you be
- **Hard floor: 14 days of scheduled puzzles per active game, at all times.** If the buffer drops below 14, the dashboard nags the editors.
- **Target: 30 days.** Build a month at the start of each month in one sitting per game.
- **Failsafe pool: ~10 evergreen puzzles per game with no `publish_date`.** If a day somehow has no scheduled puzzle, the backend automatically serves one from the pool instead of showing a 404. The game must **never** be empty. This single feature saves you the day someone forgets to schedule.
- **Editor rota:** assign each editor specific days/weeks so ownership is clear and the reviewer is always a different person.
- **Style guide:** difficulty curve (easy Mon → hard Fri/weekend), no references that only final-years get, accessibility, and a tone bar for the lore so the jokes stay fun and never punch down.

### 11d. Content is the moat
Re-stating because it matters more than any tech choice here: people who never touch Wordle will play if the answer is *about them*. Categories from campus life, mild fun lore in the easter eggs, batch rivalry on the homepage. Budget real editorial time for this, not just engineering time.

---

## 12. Repository layout

A monorepo keeps frontend, backend, and infra versioned together.

```
8bit/
├── apps/
│   └── web/                 # React (Vite) PWA  → deploys to Vercel
├── services/
│   ├── api/                 # Spring Boot modular monolith (Phases 1–5)
│   └── leaderboard/         # extracted in Phase 6
├── infra/
│   ├── docker-compose.yml   # api + redis + (optional) postgres on the VM
│   ├── cloudflared/         # tunnel config
│   └── github-actions/      # build → push to ghcr → deploy over SSH
└── docs/
    └── this-document.md
```

---

## 13. Key API surface (v1)

```
# Auth
POST /auth/register            { rollNumber, username, password }
POST /auth/login               { rollNumber, password } → access + refresh
POST /auth/refresh
POST /auth/verify-otp          { code }                  # optional email verify

# Game
GET  /puzzles/today?type=wordle            # metadata only, NO answer
POST /puzzles/{id}/guess        { guess }  # server returns colour pattern
POST /puzzles/{id}/complete                # finalise, server scores it
GET  /me/attempts

# Leaderboard
GET  /leaderboard?type=wordle&scope=campus&window=daily
GET  /leaderboard?type=wordle&scope=batch&window=alltime
GET  /leaderboard/batch-war?type=wordle    # the homepage hero

# Profile
GET  /me
GET  /users/{username}

# Notifications
POST /push/subscribe           { endpoint, p256dh, auth }

# Admin (ROLE_EDITOR / ROLE_ADMIN)
POST /admin/puzzles
PUT  /admin/puzzles/{id}
POST /admin/puzzles/{id}/submit-review
POST /admin/puzzles/{id}/approve
POST /admin/puzzles/{id}/schedule
GET  /admin/calendar?type=wordle&month=2026-07
```

---

## 14. Anti-cheat checklist (because batch competition invites cheating)

- Server-authoritative scoring; client submits moves, never scores.
- Answer never sent to the client until the round is over.
- `UNIQUE (user_id, puzzle_id)` so no replaying for a better score.
- Rate-limit guess submissions per user (Redis counter) to stop scripts.
- Sanity-check `completion_ms`; flag impossibly fast solves.
- Roll-number uniqueness + optional college-email OTP to stop fake-account batch stuffing.
- Keep an audit of suspicious attempts for editors to review before any prize goes out.

---

## 15. Deployment, end to end (all ₹0)

The order that saves you pain: **prove the empty pipeline first**, then build features into it.

**Frontend (Vercel):** connect the GitHub repo, set the project root to `apps/web`, every push to `main` auto-deploys with HTTPS. (Swap to Cloudflare Pages if you ever go commercial.)

**Backend (Oracle Always Free VM):**
1. Create an Ampere A1 instance (2 CPU / 12 GB), Ubuntu 22.04, in a region with free capacity (try Mumbai/Hyderabad/Singapore; retry if "out of capacity").
2. Install Docker + Docker Compose.
3. `docker-compose up` your Spring Boot image + Redis (+ Postgres if self-hosting). Cap JVM memory (`-Xmx512m` for the monolith; `-Xmx384m` each once you split).
4. Expose it with **Cloudflare Tunnel** (`cloudflared`): you get an HTTPS hostname pointed at the VM with no open ports, no static IP, no manual certs, and the origin IP stays hidden.

**Databases:** simplest free path is **Neon (Postgres) + Upstash (Redis)**, which keeps the VM's 12 GB free for the JVM(s). Self-hosting both in Docker on the VM is also ₹0 and gives full control; pick based on how much RAM you want to spend on the app versus the data.

**CI/CD (GitHub Actions):** on push, build the Spring Boot image, push to `ghcr.io`, SSH to the VM, pull, and restart the compose stack. Frontend deploys itself via Vercel.

**Cost ledger at the end of all this:** ₹0 recurring, except an optional domain at ~₹700–1,200/year. If you skip the domain you run on the Vercel subdomain plus the Cloudflare tunnel hostname, still ₹0. The first moment real money could appear: you exceed Vercel Hobby's 100 GB bandwidth (very unlikely for a text game) or you take revenue and must move the frontend to Pro/Cloudflare Pages. Neither happens at IIITB scale for a free game.

---

## 16. Build roadmap

- **Phase 0 — Pipeline.** Repo, CI, infra skeleton. Deploy a "hello world" frontend (Vercel) talking to a "hello world" backend (Oracle via Cloudflare Tunnel). Prove the whole path works before any features.
- **Phase 1 — Wordle core.** Server-authoritative guessing, scoring, mobile grid + keyboard, share card. Hardcode one puzzle.
- **Phase 2 — Identity + persistence.** Roll-number/username/JWT auth, batch parsing, Postgres, profile, streaks.
- **Phase 3 — Leaderboard.** Redis sorted sets, campus + batch boards, the batch-war hero (with fair normalisation).
- **Phase 4 — Editor CMS.** Create/preview/schedule, review workflow, calendar + gap warnings, failsafe evergreen pool.
- **Phase 5 — Notifications + PWA polish.** Web push, install prompt, offline-for-today.
- **Phase 6 — Microservice seam + game #2.** Extract the leaderboard service over Redis Streams/RabbitMQ. Ship Connections or Pixel Reveal. Add easter eggs.
- **Phase 7 — Hardening.** Anti-cheat, rate limits, load test the midnight spike, Actuator + Prometheus/Grafana.

Ship Phases 0 to 3 to a small pilot group (one or two batches) before opening it campus-wide. The content pipeline (Phase 4) is what you must not skip, because a great game with an empty schedule dies in week three.

---

## 17. One-paragraph version for your team

We build a mobile-first PWA (React on Vercel) backed by a Spring Boot modular monolith on an Oracle always-free VM, exposed through a Cloudflare tunnel, with Postgres for truth and Redis sorted sets for fast leaderboards. Login is roll number (which sets the batch) plus a chosen username; scoring is server-side so it can't be cheated. The batch-war leaderboard is the homepage hook, hyper-local campus content is the reason people come back, and a real editor workflow keeps at least two weeks of puzzles queued at all times with an evergreen failsafe so the game is never empty. Once it's stable we extract the leaderboard into its own service over a message stream, giving us a genuine event-driven microservices system to show off, all at ₹0 recurring cost except an optional domain.
