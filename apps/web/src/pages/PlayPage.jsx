import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api, ApiError } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import WordleGame from './WordleGame.jsx';
import ConnectionsGame from './ConnectionsGame.jsx';
import CrypticGame from './CrypticGame.jsx';
import HelpModal from '../components/HelpModal.jsx';

// Supported games. Default is wordle. The selected game lives in the
// ?game= query param so it is bookmarkable, e.g. /play?game=connections.
const GAMES = [
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
  { type: 'cryptic', label: 'Cryptic' },
];

const TITLES = {
  wordle: 'Wordle',
  connections: 'Connections',
  cryptic: 'Minute Cryptic',
};

// Which games have a How-to-Play / Tips / FAQ guide.
const HELP_GAMES = new Set(['wordle', 'connections', 'cryptic']);

export default function PlayPage() {
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const raw = (searchParams.get('game') || 'wordle').toLowerCase();
  const game = GAMES.some((g) => g.type === raw) ? raw : 'wordle';

  const [puzzle, setPuzzle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [noPuzzle, setNoPuzzle] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  // DOM node in the header that Wordle portals its 💡 hint control into, so the
  // hint lives next to the "?" instead of taking a row below the grid.
  const [headerSlot, setHeaderSlot] = useState(null);
  const hasHelp = HELP_GAMES.has(game);
  // Tracks the game whose fetch is current, so a slow response from a tab you've since switched
  // away from can't overwrite the new puzzle (the switch-leaves-old-puzzle bug, worse on mobile).
  const reqGame = useRef(game);

  const load = useCallback(async () => {
    reqGame.current = game;
    setLoading(true);
    setNoPuzzle(false);
    setPuzzle(null);
    try {
      const data = await api.getToday(game);
      if (reqGame.current !== game) return; // superseded by a newer tab switch — drop this result
      setPuzzle(data);
    } catch (err) {
      if (reqGame.current !== game) return;
      if (err instanceof ApiError) {
        if (err.code === 'NO_PUZZLE' || err.status === 404) {
          setNoPuzzle(true);
        } else if (err.code === 'RATE_LIMITED') {
          toast(err.message, { type: 'warn' });
        } else {
          toast(err.message || 'Could not load today’s puzzle', { type: 'error' });
        }
      } else {
        toast('Could not load today’s puzzle', { type: 'error' });
      }
    } finally {
      if (reqGame.current === game) setLoading(false);
    }
  }, [game, toast]);

  useEffect(() => {
    load();
  }, [load]);

  // An installed PWA is usually resumed (not reloaded) when reopened. If it's left open overnight
  // and reopened the next day, React still holds yesterday's puzzle in memory with nothing to
  // trigger a refetch. When we become visible again after the local date has changed, reload the
  // puzzle and nudge the service worker to pick up any new deploy. (Local date = IST for the
  // campus audience, matching the server's midnight rollover.)
  const loadedDay = useRef(new Date().toDateString());
  useEffect(() => {
    const onResume = () => {
      if (document.visibilityState !== 'visible') return;
      const today = new Date().toDateString();
      if (today === loadedDay.current) return;
      loadedDay.current = today;
      load();
      navigator.serviceWorker?.getRegistration()
        .then((reg) => reg?.update())
        .catch(() => {});
    };
    document.addEventListener('visibilitychange', onResume);
    window.addEventListener('focus', onResume);
    return () => {
      document.removeEventListener('visibilitychange', onResume);
      window.removeEventListener('focus', onResume);
    };
  }, [load]);

  // First-timer onboarding: auto-open the guide once per game per browser (NYT-style).
  useEffect(() => {
    if (loading || noPuzzle || !puzzle || !hasHelp) return;
    const key = '8bit.howto.' + game;
    if (!localStorage.getItem(key)) {
      setShowHelp(true);
      localStorage.setItem(key, '1');
    }
  }, [loading, noPuzzle, puzzle, game, hasHelp]);

  const selectGame = (type) => {
    setSearchParams(type === 'wordle' ? {} : { game: type });
  };

  return (
    <div className="page page--play">
      <div className="segmented play-switcher" role="tablist" aria-label="Choose game">
        {GAMES.map((g) => (
          <button
            key={g.type}
            role="tab"
            aria-selected={g.type === game}
            className={'seg' + (g.type === game ? ' seg--active' : '')}
            onClick={() => selectGame(g.type)}
          >
            {g.label}
          </button>
        ))}
      </div>

      <header className="play-header">
        <h2 className="play-title">{TITLES[game]}</h2>
        <div className="play-header__right">
          {puzzle?.date && <span className="play-sub">{puzzle.date}</span>}
          <span className="hint-slot" ref={setHeaderSlot} />
          {hasHelp && (
            <button
              className="help-btn"
              onClick={() => setShowHelp(true)}
              aria-label="How to play"
              title="How to play"
            >
              ?
            </button>
          )}
        </div>
      </header>

      {/* Heads-up when today's answer isn't a normal dictionary word (an IIITB term / brand / slang).
          Wordle & Cryptic only — Connections tiles carry their own context in the category names. */}
      {puzzle?.campusWord && (game === 'wordle' || game === 'cryptic') && (
        <p className="campus-badge" title="Today's answer may not be in the dictionary">
          🏫 Campus word — might not be in the dictionary
        </p>
      )}

      {loading && <div className="loading">Loading puzzle…</div>}

      {!loading && noPuzzle && (
        <div className="empty conn-empty">
          <p>
            {game === 'wordle'
              ? 'No Wordle puzzle today — check back tomorrow.'
              : 'No Connections puzzle today — play Wordle instead.'}
          </p>
          {game !== 'wordle' && (
            <Link className="btn btn--primary" to="/play">
              Play Wordle instead
            </Link>
          )}
        </div>
      )}

      {/* The board lives in a wrapper keyed by `game`. This is load-bearing: the board is the one
          child whose component type AND key both change on a tab switch, and it sits among sibling
          blocks (loading, empty states) that toggle on/off mid-load. React reconciles
          that keyed element against those toggling unkeyed siblings positionally and can fail to
          unmount it — leaving the previous game's grid/keyboard orphaned in the DOM while the new
          board renders below it (the "new puzzle comes below and doesn't switch" bug, worse on slow
          networks where the loading window is longer; a full refresh hid it). Keying the wrapper by
          `game` forces React to drop the whole previous-game subtree on every switch.
          The wrapper is `display:contents` so it adds no layout box (see styles.css). */}
      <div className="game-mount" key={game}>
        {!loading && puzzle && puzzle.gameType === game && (
          game === 'connections' ? (
            <ConnectionsGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
          ) : game === 'cryptic' ? (
            <CrypticGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
          ) : (
            <WordleGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} headerSlot={headerSlot} />
          )
        )}
      </div>

      {showHelp && (
        <HelpModal game={game} onClose={() => setShowHelp(false)} />
      )}
    </div>
  );
}
