import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import ResultModal from '../components/ResultModal.jsx';
import { decodeSolution, crypticCorrect, crypticHint, normalizeCryptic } from '../cipher.js';

const HINT_KINDS = [
  { kind: 'definition', label: 'Definition' },
  { kind: 'indicator', label: 'Indicator' },
  { kind: 'fodder', label: 'Fodder' },
];

// "(6)" -> [6], "(3,4)" -> [3,4], "(4-2)" -> [4,2]. Each number is a word's letter count.
function parseEnumeration(enumeration) {
  if (!enumeration) return [];
  return String(enumeration)
    .replace(/[()\s]/g, '')
    .split(/[,\-/]/)
    .map((n) => parseInt(n, 10))
    .filter((n) => Number.isInteger(n) && n > 0);
}

// Cryptic share = the clue sentence (which already ends in the enumeration, e.g. "(6)") plus your
// X/6 line. Deliberately NOT a Wordle-style grid, and it NEVER includes the answer.
function crypticShareText(solved, used, clue) {
  const n = solved ? used : 'X';
  return `8Bit Cryptic • IIITB • ${n}/6\n${clue || ''}`.trim();
}

// Build a result object (with cryptic parse) from a finished puzzle/guess payload.
function toResult(src, status, clue) {
  const solved = status === 'SOLVED';
  const used = src.guessesUsed ?? (Array.isArray(src.guesses) ? src.guesses.length : 0);
  return {
    solved,
    score: src.score,
    answer: src.answer,
    shareGrid: crypticShareText(solved, used, clue),
    status,
    parse: {
      definition: src.definition,
      indicator: src.indicator,
      fodder: src.fodder,
      device: src.device,
      explanation: src.explanation,
    },
  };
}

// MinuteCryptic-style daily clue: one clue, type the answer, three teaching hints.
export default function CrypticGame({ puzzle: initialPuzzle, reload }) {
  const { toast } = useToast();
  const { refreshUser } = useAuth();

  const [puzzle, setPuzzle] = useState(initialPuzzle);
  const [guesses, setGuesses] = useState(initialPuzzle.guesses || []);
  const [current, setCurrent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [hints, setHints] = useState(initialPuzzle.hints || []);
  const [hinting, setHinting] = useState(false);
  const [streak, setStreak] = useState(null);
  const [showResult, setShowResult] = useState(false);
  const [result, setResult] = useState(() =>
    initialPuzzle.status === 'SOLVED' || initialPuzzle.status === 'FAILED'
      ? toResult(initialPuzzle, initialPuzzle.status, initialPuzzle.clue)
      : null
  );

  const maxGuesses = puzzle?.config?.maxGuesses || 6;

  // Decrypted solution → check answers + build teaching hints locally (instant, no round-trip).
  const sol = useMemo(() => decodeSolution(initialPuzzle.solution), [initialPuzzle.solution]);
  const sendChain = useRef(Promise.resolve());

  // Letter-box answer entry sized to the enumeration, e.g. "(3,4)" -> [3,4] (7 cells in two groups).
  const segments = useMemo(() => parseEnumeration(puzzle.enumeration), [puzzle.enumeration]);
  const total = useMemo(() => segments.reduce((a, b) => a + b, 0), [segments]);
  const groups = useMemo(() => {
    let start = 0;
    return segments.map((len) => {
      const g = { start, len };
      start += len;
      return g;
    });
  }, [segments]);
  const [focused, setFocused] = useState(false);
  const inputRef = useRef(null);

  const onType = useCallback(
    (e) => {
      const letters = e.target.value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, total);
      setCurrent(letters);
    },
    [total]
  );

  const isOver = useMemo(() => {
    const s = puzzle?.status;
    return s === 'SOLVED' || s === 'FAILED';
  }, [puzzle]);

  useEffect(() => {
    if (!isOver) return;
    let alive = true;
    (async () => {
      const me = await refreshUser();
      if (alive && me?.stats) setStreak(me.stats.currentStreak);
    })();
    return () => {
      alive = false;
    };
  }, [isOver, refreshUser]);

  const hasHint = (kind) => hints.some((h) => h.kind === kind);

  const revealHint = useCallback(
    (kind) => {
      if (hinting || isOver || hints.some((h) => h.kind === kind)) return;
      // Client-side teaching hint: shows the part AND how to use it (from the decrypted device).
      if (sol) {
        const h = crypticHint(sol, kind);
        if (h && h.text) setHints((hs) => (hs.some((x) => x.kind === kind) ? hs : [...hs, h]));
        else toast(`No ${kind} hint for today’s clue`, { type: 'warn' });
        return;
      }
      setHinting(true);
      (async () => {
        try {
          const res = await api.hint(puzzle.puzzleId, kind);
          setHints(res.hints || []);
        } catch (err) {
          toast(
            err instanceof ApiError ? err.message || 'No hint available' : 'No hint available',
            { type: 'warn' }
          );
        } finally {
          setHinting(false);
        }
      })();
    },
    [hinting, isOver, hints, sol, puzzle, toast]
  );

  const submitGuess = useCallback(
    async (e) => {
      if (e) e.preventDefault();
      if (submitting || isOver || !puzzle) return;
      if (!current.trim()) {
        toast('Type your answer', { type: 'warn', duration: 1400 });
        return;
      }
      const attempt = current.trim();

      // --- Fast path: check locally, render instantly, record to the server in the background. ---
      if (sol?.answer) {
        const requiredLen = total > 0 ? total : normalizeCryptic(sol.answer).length;
        if (normalizeCryptic(attempt).length !== requiredLen) {
          toast(`Answer has ${requiredLen} letters`, { type: 'error', duration: 1600 });
          return;
        }
        const correct = crypticCorrect(attempt, sol.answer);
        const newGuesses = [...guesses, { guess: attempt.toUpperCase(), correct }];
        setGuesses(newGuesses);
        setCurrent('');
        const over = correct || newGuesses.length >= maxGuesses;

        sendChain.current = sendChain.current
          .then(() => api.guess(puzzle.puzzleId, attempt))
          .then((res) => {
            if (res?.gameOver) {
              refreshUser().then((me) => { if (me?.stats) setStreak(me.stats.currentStreak); });
            }
          })
          .catch(() => {});

        if (over) {
          const status = correct ? 'SOLVED' : 'FAILED';
          setPuzzle((p) => ({ ...p, status }));
          setResult(toResult({ ...sol, guessesUsed: newGuesses.length }, status, puzzle.clue));
          setShowResult(true);
        } else {
          toast('Not quite — try again', { type: 'warn', duration: 1400 });
        }
        return;
      }

      // --- Fallback: server-authoritative (only if the solution blob was missing). ---
      setSubmitting(true);
      try {
        const res = await api.guess(puzzle.puzzleId, attempt);
        setGuesses((prev) => [...prev, { guess: attempt.toUpperCase(), correct: res.correct }]);
        setCurrent('');

        if (res.gameOver) {
          const status = res.solved ? 'SOLVED' : 'FAILED';
          setPuzzle((p) => ({ ...p, status }));
          setResult(toResult(res, status, puzzle.clue));
          setShowResult(true);
        } else if (res.correct === false) {
          toast('Not quite — try again', { type: 'warn', duration: 1400 });
        }
      } catch (err) {
        if (err instanceof ApiError) {
          if (err.code === 'BAD_LENGTH' || err.code === 'EMPTY_GUESS') {
            toast(err.message, { type: 'error', duration: 1800 });
          } else if (err.code === 'ALREADY_FINISHED' || err.status === 409) {
            toast('Puzzle already finished — reloading.', { type: 'warn' });
            if (reload) await reload();
          } else {
            toast(err.message || 'Guess failed', { type: 'error' });
          }
        } else {
          toast('Guess failed', { type: 'error' });
        }
      } finally {
        setSubmitting(false);
      }
    },
    [submitting, isOver, puzzle, current, sol, total, guesses, maxGuesses, toast, reload, refreshUser]
  );

  const triesLeft = Math.max(0, maxGuesses - guesses.length);

  return (
    <div className="cryptic">
      <div className="cryptic-clue">
        <p className="cryptic-clue__text">{puzzle.clue}</p>
        {puzzle.enumeration && (
          <span className="cryptic-clue__enum">{puzzle.enumeration}</span>
        )}
      </div>

      {guesses.length > 0 && (
        <ul className="cryptic-guesses">
          {guesses.map((g, i) => (
            <li
              key={i}
              className={'cryptic-guess ' + (g.correct ? 'cryptic-guess--right' : 'cryptic-guess--wrong')}
            >
              <span className="cryptic-guess__word">{g.guess}</span>
              <span className="cryptic-guess__mark">{g.correct ? '✓' : '✗'}</span>
            </li>
          ))}
        </ul>
      )}

      {!isOver && (
        <>
          <form className="cryptic-answer-form" onSubmit={submitGuess}>
            {total > 0 ? (
              <div className="cryptic-answer" onClick={() => inputRef.current?.focus()}>
                <input
                  ref={inputRef}
                  className="cryptic-hidden-input"
                  value={current}
                  onChange={onType}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  maxLength={total}
                  inputMode="text"
                  autoComplete="off"
                  autoCapitalize="characters"
                  autoCorrect="off"
                  spellCheck={false}
                  disabled={submitting}
                  aria-label="Type your answer"
                />
                <div className="cryptic-cells" aria-hidden="true">
                  {groups.map((g, gi) => (
                    <div className="cryptic-cell-group" key={gi}>
                      {Array.from({ length: g.len }).map((_, li) => {
                        const idx = g.start + li;
                        const active = focused && idx === current.length;
                        return (
                          <span
                            key={li}
                            className={
                              'cryptic-cell' +
                              (current[idx] ? ' cryptic-cell--filled' : '') +
                              (active ? ' cryptic-cell--active' : '')
                            }
                          >
                            {current[idx] || ''}
                          </span>
                        );
                      })}
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <input
                className="cryptic-input"
                type="text"
                autoComplete="off"
                autoCapitalize="characters"
                placeholder="Your answer"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
                disabled={submitting}
              />
            )}
            <button className="btn btn--primary" type="submit" disabled={submitting}>
              Guess
            </button>
          </form>
          <p className="cryptic-tries">{triesLeft} {triesLeft === 1 ? 'try' : 'tries'} left</p>

          <div className="hint-bar">
            <div className="hint-bar__buttons">
              {HINT_KINDS.map((h) => (
                <button
                  key={h.kind}
                  className="btn btn--small btn--ghost"
                  onClick={() => revealHint(h.kind)}
                  disabled={hinting || hasHint(h.kind)}
                >
                  💡 {h.label}
                </button>
              ))}
            </div>
            {hints.length > 0 && (
              <div className="cryptic-hints">
                {hints.map((h) => (
                  <div key={h.kind} className="cryptic-hint">
                    <span className="cryptic-hint__kind">{h.kind}</span>
                    <span className="cryptic-hint__text">{h.text}</span>
                    {h.how && <span className="cryptic-hint__how">{h.how}</span>}
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {isOver && !showResult && (
        <button className="btn btn--primary" onClick={() => setShowResult(true)}>
          View result
        </button>
      )}

      {showResult && result && (
        <ResultModal
          result={result}
          streak={streak}
          puzzle={puzzle}
          onClose={() => setShowResult(false)}
        />
      )}
    </div>
  );
}
