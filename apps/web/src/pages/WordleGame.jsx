import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import WordleGrid from '../components/WordleGrid.jsx';
import Keyboard, { computeKeyStates } from '../components/Keyboard.jsx';
import ResultModal from '../components/ResultModal.jsx';
import EasterEggModal from '../components/EasterEggModal.jsx';

// The Wordle game. Receives the already-loaded /today payload plus a reload fn
// so the parent PlayPage owns fetching for whichever game type is selected.
export default function WordleGame({ puzzle: initialPuzzle, reload }) {
  const { toast } = useToast();
  const { refreshUser } = useAuth();

  const [puzzle, setPuzzle] = useState(initialPuzzle);
  const [guesses, setGuesses] = useState(initialPuzzle.guesses || []);
  const [current, setCurrent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [shake, setShake] = useState(false);
  const [showResult, setShowResult] = useState(false);
  const [result, setResult] = useState(() =>
    initialPuzzle.status === 'SOLVED' || initialPuzzle.status === 'FAILED'
      ? {
          score: initialPuzzle.score,
          solved: initialPuzzle.status === 'SOLVED',
          answer: initialPuzzle.answer,
          shareGrid: null,
          status: initialPuzzle.status,
        }
      : null
  );
  const [streak, setStreak] = useState(null);
  const [egg, setEgg] = useState(null);
  const [hints, setHints] = useState(initialPuzzle.hints || []);
  const [hinting, setHinting] = useState(false);
  const [warning, setWarning] = useState(null);

  const wordLength = puzzle?.config?.wordLength || 5;
  const maxGuesses = puzzle?.config?.maxGuesses || 6;

  const hasHint = (kind) => hints.some((h) => h.kind === kind);

  const isOver = useMemo(() => {
    const s = puzzle?.status;
    return s === 'SOLVED' || s === 'FAILED';
  }, [puzzle]);

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

  // Pull streak for result screen.
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

  const triggerShake = useCallback(() => {
    setShake(true);
    setTimeout(() => setShake(false), 450);
  }, []);

  const submitGuess = useCallback(async () => {
    if (submitting || isOver || !puzzle) return;
    if (current.length !== wordLength) {
      triggerShake();
      toast('Not enough letters', { type: 'warn', duration: 1400 });
      return;
    }
    setSubmitting(true);
    const attempt = current.toUpperCase();
    try {
      const res = await api.guess(puzzle.puzzleId, attempt);
      const newGuesses = [...guesses, { guess: attempt, result: res.result }];
      setGuesses(newGuesses);
      setCurrent('');

      if (res.easterEgg) setEgg(res.easterEgg);
      if (res.warning) {
        setWarning(res.warning);
        toast(res.warning, { type: 'warn', duration: 7000 });
      }

      if (res.gameOver) {
        const status = res.status || (res.solved ? 'SOLVED' : 'FAILED');
        setPuzzle((p) => ({ ...p, status, score: res.score, answer: res.answer }));
        setResult({
          score: res.score,
          solved: res.solved,
          answer: res.answer,
          shareGrid: res.shareGrid,
          status,
        });
        setShowResult(true);
      } else {
        setPuzzle((p) => ({ ...p, status: res.status || 'IN_PROGRESS' }));
      }
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'NOT_IN_WORD_LIST' || err.code === 'BAD_LENGTH') {
          triggerShake();
          toast(err.message || 'Invalid word', { type: 'error', duration: 1800 });
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
  }, [
    submitting,
    isOver,
    puzzle,
    current,
    wordLength,
    guesses,
    triggerShake,
    toast,
    reload,
  ]);

  const handleKey = useCallback(
    (key) => {
      if (isOver || submitting) return;
      if (key === 'ENTER') {
        submitGuess();
        return;
      }
      if (key === 'BACK') {
        setCurrent((c) => c.slice(0, -1));
        return;
      }
      if (/^[A-Z]$/.test(key)) {
        setCurrent((c) => (c.length < wordLength ? c + key : c));
      }
    },
    [isOver, submitting, submitGuess, wordLength]
  );

  const handleKeyRef = useRef(handleKey);
  handleKeyRef.current = handleKey;
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      let k = e.key;
      if (k === 'Enter') k = 'ENTER';
      else if (k === 'Backspace') k = 'BACK';
      else if (/^[a-zA-Z]$/.test(k)) k = k.toUpperCase();
      else return;
      e.preventDefault();
      handleKeyRef.current(k);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const keyStates = useMemo(() => computeKeyStates(guesses), [guesses]);

  return (
    <>
      {warning && (
        <div className="warn-banner" role="alert">
          ⚠️ {warning}
        </div>
      )}

      <WordleGrid
        guesses={guesses}
        current={current}
        wordLength={wordLength}
        maxGuesses={maxGuesses}
        shakeRow={shake}
      />

      {!isOver && (
        <div className="hint-bar">
          <div className="hint-bar__buttons">
            <button
              className="btn btn--small btn--ghost"
              onClick={() => revealHint('vowel')}
              disabled={hinting || hasHint('vowel')}
            >
              💡 Reveal a vowel
            </button>
            <button
              className="btn btn--small btn--ghost"
              onClick={() => revealHint('consonant')}
              disabled={hinting || hasHint('consonant')}
            >
              💡 Reveal a consonant
            </button>
          </div>
          {hints.length > 0 && (
            <div className="hint-chips">
              {hints.map((h) => (
                <span key={h.kind} className={'hint-chip hint-chip--' + h.kind}>
                  Contains <strong>{h.letter}</strong>
                  <span className="hint-chip__kind"> ({h.kind})</span>
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      {isOver && !showResult && (
        <button className="btn btn--primary" onClick={() => setShowResult(true)}>
          View result
        </button>
      )}

      <Keyboard keyStates={keyStates} onKey={handleKey} disabled={isOver || submitting} />

      {showResult && result && (
        <ResultModal
          result={result}
          streak={streak}
          puzzle={puzzle}
          onClose={() => setShowResult(false)}
        />
      )}

      {egg && <EasterEggModal egg={egg} onClose={() => setEgg(null)} />}
    </>
  );
}
