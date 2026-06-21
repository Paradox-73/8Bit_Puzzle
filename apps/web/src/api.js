// Lightweight fetch wrapper with auth header + 401 handling.

export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

// Leaderboard may be a separate service in production. Falls back to API_BASE.
export const LEADERBOARD_BASE = import.meta.env.VITE_LEADERBOARD_BASE || API_BASE;

const TOKEN_KEY = '8bit.accessToken';
const USER_KEY = '8bit.user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function getStoredUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setStoredUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
}

// Custom error so callers can read code/message/status from the contract.
export class ApiError extends Error {
  constructor(message, { status, code, body } = {}) {
    super(message || 'Request failed');
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

// Called on 401 so the app can redirect to /login. Wired up by auth provider.
let onUnauthorized = null;
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

/**
 * Core request helper.
 * @param {string} path e.g. "/puzzles/today"
 * @param {object} opts { method, body, auth, query, base }
 */
export async function request(path, opts = {}) {
  const { method = 'GET', body, auth = true, query, base = API_BASE } = opts;

  let url = base + path;
  if (query && typeof query === 'object') {
    const qs = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') qs.append(k, v);
    });
    const s = qs.toString();
    if (s) url += (path.includes('?') ? '&' : '?') + s;
  }

  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (networkErr) {
    throw new ApiError('Network error — are you offline?', {
      status: 0,
      code: 'NETWORK',
      body: { message: String(networkErr) },
    });
  }

  if (res.status === 401) {
    if (onUnauthorized) onUnauthorized();
    throw new ApiError('Session expired. Please log in again.', {
      status: 401,
      code: 'UNAUTHORIZED',
    });
  }

  // Global rate-limit handling so any page can toast it.
  if (res.status === 429) {
    throw new ApiError('Slow down — too many requests.', {
      status: 429,
      code: 'RATE_LIMITED',
    });
  }

  // Some endpoints (DELETE, 204) may have no body.
  let data = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!res.ok) {
    const code = (data && data.error) || 'ERROR';
    const message = (data && data.message) || `Request failed (${res.status})`;
    throw new ApiError(message, { status: res.status, code, body: data });
  }

  return data;
}

// ---- Convenience endpoint helpers ----

export const api = {
  // AUTH
  register: (payload) =>
    request('/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) =>
    request('/auth/login', { method: 'POST', body: payload, auth: false }),

  // EMAIL OTP VERIFICATION
  verifyOtp: (code) =>
    request('/auth/verify-otp', { method: 'POST', body: { code } }),
  resendOtp: () => request('/auth/resend-otp', { method: 'POST' }),

  // GAME
  getToday: (type = 'wordle') => request('/puzzles/today', { query: { type } }),
  guess: (puzzleId, guess) =>
    request(`/puzzles/${puzzleId}/guess`, { method: 'POST', body: { guess } }),
  // Generic move: posts an arbitrary JSON body as-is (e.g. Connections selection).
  move: (puzzleId, body) =>
    request(`/puzzles/${puzzleId}/guess`, { method: 'POST', body }),

  // LEADERBOARD (may be a separate service — uses LEADERBOARD_BASE)
  leaderboard: ({ type = 'wordle', scope = 'campus', window = 'daily' }) =>
    request('/leaderboard', { query: { type, scope, window }, base: LEADERBOARD_BASE }),
  batchWar: (type = 'wordle') =>
    request('/leaderboard/batch-war', { query: { type }, base: LEADERBOARD_BASE }),

  // PROFILE
  me: () => request('/me'),
  user: (username) => request(`/users/${encodeURIComponent(username)}`),

  // PUSH
  vapidKey: () => request('/push/vapid-public-key'),
  subscribePush: (sub) => request('/push/subscribe', { method: 'POST', body: sub }),

  // ADMIN
  adminCalendar: ({ type = 'wordle', month }) =>
    request('/admin/calendar', { query: { type, month } }),
  adminPuzzles: ({ type = 'wordle', month }) =>
    request('/admin/puzzles', { query: { type, month } }),
  adminCreatePuzzle: (payload) =>
    request('/admin/puzzles', { method: 'POST', body: payload }),
  adminUpdatePuzzle: (id, payload) =>
    request(`/admin/puzzles/${id}`, { method: 'PUT', body: payload }),
  adminSubmitReview: (id) =>
    request(`/admin/puzzles/${id}/submit-review`, { method: 'POST' }),
  adminApprove: (id) => request(`/admin/puzzles/${id}/approve`, { method: 'POST' }),
  adminSchedule: (id) => request(`/admin/puzzles/${id}/schedule`, { method: 'POST' }),
  adminDeletePuzzle: (id) => request(`/admin/puzzles/${id}`, { method: 'DELETE' }),
};
