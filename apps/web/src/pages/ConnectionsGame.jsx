import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import ConnectionsBoard from '../components/ConnectionsBoard.jsx';
import EasterEggModal from '../components/EasterEggModal.jsx';
import ConnectionsResultModal from '../components/ConnectionsResultModal.jsx';
import { decodeSolution, connectionsGroups, judgeConnections } from '../cipher.js';

// Returns the set of all tile strings that belong to already-solved groups.
function solvedMembers(solvedGroups) {
  const set = new Set();
  for (const g of solvedGroups || []) {
    for (const m of g.members || []) set.add(m);
  }
  return set;
}

// Fisher–Yates shuffle (client-side shuffle button only).
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export default function ConnectionsGame({ puzzle: initialPuzzle, reload }) {
  const { toast } = useToast();
  const { refreshUser } = useAuth();

  const [puzzle, setPuzzle] = useState(initialPuzzle);
  const [solvedGroups, setSolvedGroups] = useState(initialPuzzle.solvedGroups || []);
  const [selected, setSelected] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [shake, setShake] = useState(false);
  const [popOut, setPopOut] = useState([]);
  const [egg, setEgg] = useState(null);
  const [showResult, setShowResult] = useState(false);
  const [result, setResult] = useState(null);
  const [streak, setStreak] = useState(null);
  const [hints, setHints] = useState(initialPuzzle.hints || []);
  const [hinting, setHinting] = useState(false);

  // Decrypted groups → judge selections + hints locally (instant). Queue of groups still to reveal
  // one-tap-at-a-time once the game ends.
  const sol = useMemo(() => decodeSolution(initialPuzzle.solution), [initialPuzzle.solution]);
  const groups = useMemo(() => connectionsGroups(sol), [sol]);
  const sendChain = useRef(Promise.resolve());

  const groupSize = puzzle?.config?.groupSize || 4;
  const maxMistakes = puzzle?.config?.maxMistakes || 4;

  const [mistakesUsed, setMistakesUsed] = useState(initialPuzzle.mistakesUsed || 0);

  // Remaining (unsolved) tiles, kept in a stateful display order so Shuffle works.
  const [tiles, setTiles] = useState(() => {
    const solved = solvedMembers(initialPuzzle.solvedGroups);
    return (initialPuzzle.tiles || []).filter((t) => !solved.has(t));
  });

  const isOver = useMemo(() => {
    const s = puzzle?.status;
    return s === 'SOLVED' || s === 'FAILED';
  }, [puzzle]);

  // Restore the result modal if the game was already finished on load.
  useEffect(() => {
    if (
      (initialPuzzle.status === 'SOLVED' || initialPuzzle.status === 'FAILED') &&
      initialPuzzle.score != null
    ) {
      setResult({
        solved: initialPuzzle.status === 'SOLVED',
        score: initialPuzzle.score,
        shareGrid: null,
        status: initialPuzzle.status,
        solvedGroups: initialPuzzle.solvedGroups || [],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  const toggle = useCallback(
    (tile) => {
      if (isOver || submitting) return;
      setSelected((sel) => {
        if (sel.includes(tile)) return sel.filter((t) => t !== tile);
        if (sel.length >= groupSize) return sel; // cap at groupSize
        return [...sel, tile];
      });
    },
    [isOver, submitting, groupSize]
  );

  const deselectAll = useCallback(() => setSelected([]), []);

  const doShuffle = useCallback(() => {
    setTiles((t) => shuffle(t));
  }, []);

  // Client-side hint, tap-by-tap: each tap reveals ONE more group's anchor word (lowest level first),
  // instead of revealing all four at once.
  const revealHint = useCallback(() => {
    if (hinting || isOver) return;
    if (groups.length) {
      const anchored = new Set(hints.map((h) => h.level));
      const solvedLv = new Set(solvedGroups.map((g) => g.level));
      const cand = groups
        .filter((g) => !solvedLv.has(g.level) && !anchored.has(g.level))
        .sort((a, b) => (a.level ?? 0) - (b.level ?? 0));
      if (!cand.length) return;
      const g = cand[0];
      const word = g.members[Math.floor(Math.random() * g.members.length)];
      setHints((hs) => [...hs, { level: g.level, word }]);
      return;
    }
    setHinting(true);
    (async () => {
      try {
        const res = await api.hint(puzzle.puzzleId, 'anchors');
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
  }, [hinting, isOver, hints, groups, solvedGroups, puzzle, toast]);

  // Loss reveal: dump the missed groups one after another automatically (like NYT Connections),
  // then show the result modal.
  const revealMissedSequentially = useCallback((queue) => {
    if (!queue.length) {
      setShowResult(true);
      return;
    }
    const [next, ...rest] = queue;
    setSolvedGroups((sg) => [...sg, next]);
    setTiles((t) => t.filter((x) => !next.members.includes(x)));
    setTimeout(() => revealMissedSequentially(rest), 700);
  }, []);

  // Whether another anchor hint can still be revealed (progressive).
  const canHint = useMemo(() => {
    if (!groups.length) return hints.length === 0;
    const solvedLv = new Set(solvedGroups.map((g) => g.level));
    const anchored = new Set(hints.map((h) => h.level));
    return groups.some((g) => !solvedLv.has(g.level) && !anchored.has(g.level));
  }, [groups, solvedGroups, hints]);

  const LEVEL_NAMES = ['Yellow', 'Green', 'Blue', 'Purple'];

  const triggerShake = useCallback(() => {
    setShake(true);
    setTimeout(() => setShake(false), 450);
  }, []);

  const submit = useCallback(async () => {
    if (submitting || isOver || selected.length !== groupSize) return;
    const selection = selected.slice();

    // --- Fast path: judge locally, render instantly, record to the server in the background. ---
    if (groups.length) {
      const judged = judgeConnections(groups, selection);
      let newSolved = solvedGroups;
      let newMistakes = mistakesUsed;

      if (judged.correct) {
        newSolved = [...solvedGroups, judged.group];
        setSolvedGroups(newSolved);
        setPopOut(selection);
        setTimeout(() => {
          setTiles((t) => t.filter((x) => !selection.includes(x)));
          setPopOut([]);
        }, 350);
        setSelected([]);
      } else {
        newMistakes = mistakesUsed + 1;
        setMistakesUsed(newMistakes);
        if (judged.oneAway) {
          toast('One away…', { type: 'warn', duration: 1600 });
          triggerShake();
        } else {
          triggerShake();
          setTimeout(() => setSelected([]), 450);
        }
      }

      const won = newSolved.length === 4;
      const over = won || newMistakes >= maxMistakes;

      sendChain.current = sendChain.current
        .then(() => api.move(puzzle.puzzleId, { selection }))
        .then((res) => {
          if (res?.easterEgg) setEgg(res.easterEgg);
          if (res?.gameOver) {
            refreshUser().then((me) => { if (me?.stats) setStreak(me.stats.currentStreak); });
          }
        })
        .catch(() => {});

      if (over) {
        const allGroups = groups.slice().sort((a, b) => (a.level ?? 0) - (b.level ?? 0));
        setPuzzle((p) => ({ ...p, status: won ? 'SOLVED' : 'FAILED' }));
        setResult({ solved: won, shareGrid: null, status: won ? 'SOLVED' : 'FAILED', solvedGroups: allGroups });
        if (won) {
          setShowResult(true);
        } else {
          // Loss: auto-reveal the missed groups in sequence, then open the result.
          const solvedLv = new Set(newSolved.map((g) => g.level));
          const missed = allGroups.filter((g) => !solvedLv.has(g.level));
          setTimeout(() => revealMissedSequentially(missed), 500);
        }
      }
      return;
    }

    // --- Fallback: server-authoritative (only if the solution blob was missing). ---
    setSubmitting(true);
    try {
      const res = await api.move(puzzle.puzzleId, { selection });

      if (typeof res.mistakesUsed === 'number') setMistakesUsed(res.mistakesUsed);
      if (res.solvedGroups) setSolvedGroups(res.solvedGroups);
      if (res.easterEgg) setEgg(res.easterEgg);

      if (res.correct) {
        setPopOut(selection);
        setTimeout(() => {
          setTiles((t) => t.filter((x) => !selection.includes(x)));
          setPopOut([]);
        }, 350);
        setSelected([]);
      } else if (res.oneAway) {
        toast('One away…', { type: 'warn', duration: 1600 });
        triggerShake();
      } else {
        triggerShake();
        setTimeout(() => setSelected([]), 450);
      }

      if (res.gameOver) {
        const status = res.status || (res.solved ? 'SOLVED' : 'FAILED');
        setPuzzle((p) => ({ ...p, status, score: res.score }));
        const finalGroups = res.solvedGroups || solvedGroups;
        setResult({
          solved: !!res.solved,
          score: res.score,
          shareGrid: res.shareGrid,
          status,
          solvedGroups: finalGroups,
        });
        const revealed = solvedMembers(finalGroups);
        setTiles((t) => t.filter((x) => !revealed.has(x)));
        setShowResult(true);
      } else if (res.status) {
        setPuzzle((p) => ({ ...p, status: res.status }));
      }
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'BAD_SELECTION' || err.status === 400) {
          triggerShake();
          toast(err.message || 'Pick exactly 4 valid tiles', { type: 'error', duration: 1800 });
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
    selected,
    groupSize,
    groups,
    solvedGroups,
    mistakesUsed,
    maxMistakes,
    puzzle,
    toast,
    triggerShake,
    reload,
    refreshUser,
    revealMissedSequentially,
  ]);

  const mistakeDots = Array.from({ length: maxMistakes }, (_, i) => i < mistakesUsed);

  return (
    <>
      <div className="conn-mistakes" aria-label={`${mistakesUsed} of ${maxMistakes} mistakes used`}>
        <span className="conn-mistakes__label">Mistakes</span>
        <span className="conn-mistakes__dots" aria-hidden="true">
          {mistakeDots.map((used, i) => (
            <span
              key={i}
              className={'conn-dot' + (used ? ' conn-dot--used' : '')}
            />
          ))}
        </span>
      </div>

      {hints.length > 0 && (
        <div className="conn-anchors">
          <span className="conn-anchors__label">One word per group:</span>
          <div className="conn-anchors__chips">
            {hints
              .slice()
              .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
              .map((h) => (
                <span key={h.word} className={'conn-anchor conn-anchor--l' + (h.level ?? 0)}>
                  {h.word}
                  <span className="conn-anchor__lvl"> · {LEVEL_NAMES[h.level ?? 0]}</span>
                </span>
              ))}
          </div>
        </div>
      )}

      <ConnectionsBoard
        tiles={tiles}
        solvedGroups={solvedGroups}
        selected={selected}
        onToggle={toggle}
        shakeSelected={shake}
        popOut={popOut}
        disabled={isOver || submitting}
      />

      {isOver ? (
        !showResult && (
          <button className="btn btn--primary" onClick={() => setShowResult(true)}>
            View result
          </button>
        )
      ) : (
        <div className="conn-controls">
          <button
            className="btn btn--ghost"
            onClick={doShuffle}
            disabled={submitting || tiles.length === 0}
          >
            ⤮ Shuffle
          </button>
          <button
            className="btn btn--ghost"
            onClick={deselectAll}
            disabled={submitting || selected.length === 0}
          >
            ✕ Deselect all
          </button>
          <button
            className="btn btn--ghost"
            onClick={revealHint}
            disabled={hinting || !canHint}
          >
            💡 Hint{hints.length > 0 ? ` (${hints.length})` : ''}
          </button>
          <button
            className="btn btn--primary"
            onClick={submit}
            disabled={submitting || selected.length !== groupSize}
          >
            Submit
          </button>
        </div>
      )}

      {showResult && result && (
        <ConnectionsResultModal
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
