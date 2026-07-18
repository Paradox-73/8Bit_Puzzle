// Client mirror of the server's PayloadCipher. The `/today` payload carries the day's solution as an
// obfuscated `solution` blob so the browser can evaluate guesses + hints locally with ZERO latency
// (like NYT Wordle). This is obfuscation, not security — the key is right here in the bundle. Fine:
// the run has no rewards and every finished result is still recorded server-side.
// The key MUST match services/api PayloadCipher.
const CIPHER_KEY = '8bit-iiitb-daily-2026-xor-key';

/** Decode the obfuscated `solution` blob from /today back into the puzzle content object. */
export function decodeSolution(b64) {
  if (!b64) return null;
  try {
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      bytes[i] = bin.charCodeAt(i) ^ CIPHER_KEY.charCodeAt(i % CIPHER_KEY.length);
    }
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

// ---- Wordle (mirrors WordleEngine: two-pass green-then-yellow, repeat-letter safe) ----
export function scoreWordle(guess, answer) {
  const g = (guess || '').toUpperCase();
  const a = (answer || '').toUpperCase();
  const n = a.length;
  const res = new Array(n).fill('GREY');
  const remaining = {};
  for (let i = 0; i < n; i++) if (g[i] !== a[i]) remaining[a[i]] = (remaining[a[i]] || 0) + 1;
  for (let i = 0; i < n; i++) if (g[i] === a[i]) res[i] = 'GREEN';
  for (let i = 0; i < n; i++) {
    if (res[i] === 'GREEN') continue;
    const c = g[i];
    if (remaining[c] > 0) { res[i] = 'YELLOW'; remaining[c]--; }
  }
  return res;
}
export const wordleSolved = (pattern) => pattern.length > 0 && pattern.every((s) => s === 'GREEN');

const VOWELS = new Set(['A', 'E', 'I', 'O', 'U']);
/** Reveal one contains-letter of the given kind ('vowel'|'consonant'); never its position. */
export function wordleHint(answer, kind, existing = []) {
  const already = existing.find((h) => h.kind === kind);
  if (already) return already;
  const a = (answer || '').toUpperCase();
  const wantVowel = kind === 'vowel';
  const cands = [...a].filter((ch) => VOWELS.has(ch) === wantVowel);
  if (!cands.length) return null;
  return { kind, letter: cands[Math.floor(Math.random() * cands.length)] };
}

// ---- Connections ----
export function connectionsGroups(sol) {
  return (sol?.groups || []).map((g) => ({
    level: g.level,
    category: g.category,
    members: (g.members || []).map((m) => String(m).toUpperCase()),
  }));
}
/** Judge a 4-tile selection: exact match, one-away (3/4), and the matched group if exact. */
export function judgeConnections(groups, selection) {
  const sel = new Set(selection.map((s) => s.toUpperCase()));
  let best = 0;
  let exact = null;
  for (const g of groups) {
    const c = g.members.filter((m) => sel.has(m)).length;
    best = Math.max(best, c);
    if (g.members.length === sel.size && g.members.every((m) => sel.has(m))) exact = g;
  }
  return { correct: !!exact, oneAway: !exact && best === 3, group: exact };
}
/** One random anchor word per still-unsolved group (by level), for the Hint button. */
export function connectionsAnchors(groups, solvedLevels = []) {
  const solved = new Set(solvedLevels);
  return groups
    .filter((g) => !solved.has(g.level))
    .map((g) => ({ level: g.level, word: g.members[Math.floor(Math.random() * g.members.length)] }));
}

// ---- Cryptic ----
export const normalizeCryptic = (s) => (s || '').toUpperCase().replace(/[^A-Z]/g, '');
export const crypticCorrect = (guess, answer) =>
  normalizeCryptic(guess) === normalizeCryptic(answer);

// How each wordplay device transforms the fodder (MinuteCryptic-style teaching, no answer given).
const DEVICE_GUIDE = {
  Anagram: 'Rearrange the fodder’s letters to spell the answer.',
  Hidden: 'The answer is hiding in a run of consecutive letters inside the fodder.',
  Homophone: 'The answer sounds like the fodder when you say it aloud.',
  Reversal: 'Read the fodder backwards to get the answer.',
  Charade: 'Break the fodder into pieces and join them one after another.',
  Container: 'Put one piece of the fodder inside another.',
  Deletion: 'Drop a letter from the fodder — its head, tail, or middle.',
};
/**
 * A cryptic hint that teaches how to USE the part, not just what it is (like Minute Cryptic).
 * Returns { kind, text, how } for 'definition' | 'indicator' | 'fodder'.
 */
export function crypticHint(sol, kind) {
  if (!sol) return null;
  const device = sol.device || '';
  const guide = DEVICE_GUIDE[device] ||
    'Work out which wordplay the indicator is calling for, then apply it to the fodder.';
  if (kind === 'definition') {
    return {
      kind,
      text: sol.definition,
      how: 'The straight definition — the answer means this. It’s always at the very start or the very end of the clue.',
    };
  }
  if (kind === 'indicator') {
    return {
      kind,
      text: sol.indicator,
      how: `This word tells you the wordplay${device ? ` (${device})` : ''}: ${guide}`,
    };
  }
  if (kind === 'fodder') {
    return {
      kind,
      text: sol.fodder,
      how: `These are the raw letters/words to work on. ${guide}`,
    };
  }
  return null;
}
