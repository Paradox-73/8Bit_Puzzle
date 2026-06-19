// Midnight-spike load test for the 8Bit backend.
//
// Simulates the only load profile that matters for this game: "almost nothing, then EVERYONE
// at once when the puzzle drops." Ramps to 1000 concurrent virtual users, each of whom registers,
// fetches today's puzzle, and plays a full round (up to 6 guesses -> finalize -> leaderboard event).
//
// Run (install k6 first - see README "Scalability"):
//   k6 run -e BASE=http://localhost:8080 infra/loadtest/midnight-spike.js
//
// What "1000 concurrent" really means here: 1000 students each firing a short burst of ~7 cheap
// requests over a few minutes. That is a few hundred req/s at peak -- trivial for one tuned JVM.

import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE || "http://localhost:8080";

// A handful of legal guesses from the server's word list (mostly wrong on purpose, so every
// VU plays the full 6-guess path and triggers a finalize + leaderboard write).
const GUESSES = ["ABOUT", "OTHER", "WHICH", "THEIR", "ROBOT", "POWER"];

export const options = {
  scenarios: {
    midnight: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "1m", target: 1000 }, // the rush when the puzzle drops
        { duration: "2m", target: 1000 }, // everyone playing
        { duration: "30s", target: 0 },   // tail off
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],          // < 1% errors
    http_req_duration: ["p(95)<800"],        // 95% of requests under 800ms
    "http_req_duration{name:guess}": ["p(95)<500"],
  },
};

function headers(token) {
  return { headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` } };
}

export default function () {
  // Unique-ish identity per VU+iteration. Roll format must match the server regex.
  const n = (__VU * 100000 + __ITER) % 9999;
  const roll = `IMT2023${String(n).padStart(4, "0")}`;
  const username = `load_${__VU}_${__ITER}`;
  // Throwaway per-run credential for synthetic load users (override with LOAD_PW).
  const pw = __ENV.LOAD_PW || `k6-${__VU}-${__ITER}-pw`;
  const j = { "Content-Type": "application/json" };

  // Register (or fall back to login if this identity already exists).
  let res = http.post(`${BASE}/auth/register`, JSON.stringify({ rollNumber: roll, username, password: pw }), { headers: j, tags: { name: "register" } });
  if (res.status !== 200) {
    res = http.post(`${BASE}/auth/login`, JSON.stringify({ rollNumber: roll, password: pw }), { headers: j, tags: { name: "login" } });
  }
  check(res, { "auth ok": (r) => r.status === 200 });
  const body = res.json();
  if (!body || !body.accessToken) return;
  const token = body.accessToken;

  // Fetch today's puzzle.
  res = http.get(`${BASE}/puzzles/today?type=wordle`, { ...headers(token), tags: { name: "today" } });
  check(res, { "today ok": (r) => r.status === 200 });
  const today = res.json();
  if (!today || !today.puzzleId) return;

  // Play the round.
  for (let i = 0; i < GUESSES.length; i++) {
    res = http.post(`${BASE}/puzzles/${today.puzzleId}/guess`, JSON.stringify({ guess: GUESSES[i] }), { ...headers(token), tags: { name: "guess" } });
    // 200 = accepted; 400 = not-in-word-list (won't happen here); 409 = already finished.
    if (res.status === 200) {
      const g = res.json();
      if (g.gameOver) break;
    } else {
      break;
    }
    sleep(0.3); // a human thinks between guesses
  }

  // Read the leaderboard like a real player would after finishing.
  http.get(`${BASE}/leaderboard?type=wordle&scope=campus&window=daily`, { ...headers(token), tags: { name: "leaderboard" } });
  sleep(1);
}
