import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import ResultModal from '../components/ResultModal.jsx';

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

// Build a result object (with cryptic parse) from a finished puzzle/guess payload.
function toResult(src, status) {
  return {
    solved: status === 'SOLVED',
    score: src.score,
    answer: src.answer,
    shareGrid: src.shareGrid || null,
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
      ? toResult(initialPuzzle, initialPuzzle.status)
      : null
  );

  const maxGuesses = puzzle?.config?.maxGuesses || 6;

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
    async (kind) => {
      if (hinting || isOver || hints.some((h) => h.kind === kind)) return;
      setHinting(true);
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
    },
    [hinting, isOver, hints, puzzle, toast]
  );

  const submitGuess = useCallback(
    async (e) => {
      if (e) e.preventDefault();
      if (submitting || isOver || !puzzle) return;
      if (!current.trim()) {
        toast('Type your answer', { type: 'warn', duration: 1400 });
        return;
      }
      setSubmitting(true);
      const attempt = current.trim();
      try {
        const res = await api.guess(puzzle.puzzleId, attempt);
        setGuesses((prev) => [...prev, { guess: attempt.toUpperCase(), correct: res.correct }]);
        setCurrent('');

        if (res.gameOver) {
          const status = res.solved ? 'SOLVED' : 'FAILED';
          setPuzzle((p) => ({ ...p, status }));
          setResult(toResult(res, status));
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
    [submitting, isOver, puzzle, current, toast, reload]
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
