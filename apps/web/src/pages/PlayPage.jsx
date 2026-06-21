import { useCallback, useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api, ApiError } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import WordleGame from './WordleGame.jsx';
import ConnectionsGame from './ConnectionsGame.jsx';

// Supported games. Default is wordle. The selected game lives in the
// ?game= query param so it is bookmarkable, e.g. /play?game=connections.
const GAMES = [
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
];

const TITLES = {
  wordle: 'Wordle',
  connections: 'Connections',
};

export default function PlayPage() {
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const raw = (searchParams.get('game') || 'wordle').toLowerCase();
  const game = GAMES.some((g) => g.type === raw) ? raw : 'wordle';

  const [puzzle, setPuzzle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [noPuzzle, setNoPuzzle] = useState(false);

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
        {puzzle?.date && <span className="play-sub">{puzzle.date}</span>}
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

      {!loading && puzzle && puzzle.gameType !== 'connections' && (
        <WordleGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
      )}
    </div>
  );
}
