import { useCallback, useEffect, useState } from 'react';
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

  const load = useCallback(async () => {
    setLoading(true);
    setNoPuzzle(false);
    setPuzzle(null);
    try {
      const data = await api.getToday(game);
      setPuzzle(data);
    } catch (err) {
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
      setLoading(false);
    }
  }, [game, toast]);

  useEffect(() => {
    load();
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

      {!loading && puzzle && puzzle.gameType === 'connections' && (
        <ConnectionsGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
      )}

      {!loading && puzzle && puzzle.gameType === 'cryptic' && (
        <CrypticGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
      )}

      {!loading && puzzle && puzzle.gameType !== 'connections' && puzzle.gameType !== 'cryptic' && (
        <WordleGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} headerSlot={headerSlot} />
      )}

      {showHelp && (
        <HelpModal game={game} onClose={() => setShowHelp(false)} />
      )}
    </div>
  );
}
