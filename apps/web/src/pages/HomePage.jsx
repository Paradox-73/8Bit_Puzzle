import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';
import BatchWarBar from '../components/BatchWarBar.jsx';

const STATUS_CTA = {
  NOT_STARTED: 'Play today’s puzzle',
  IN_PROGRESS: 'Continue puzzle',
  SOLVED: 'View result',
  FAILED: 'View result',
};

const GAMES = [
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
];

const TITLES = { wordle: 'Wordle', connections: 'Connections' };

export default function HomePage() {
  const { user } = useAuth();
  const [game, setGame] = useState('wordle');
  const [today, setToday] = useState(null);
  const [batchWar, setBatchWar] = useState(null);
  const [loading, setLoading] = useState(true);
  const [noPuzzle, setNoPuzzle] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setNoPuzzle(false);
    setToday(null);
    (async () => {
      try {
        const [t, bw] = await Promise.allSettled([
          api.getToday(game),
          api.batchWar(game),
        ]);
        if (!alive) return;
        if (t.status === 'fulfilled') {
          setToday(t.value);
        } else if (
          t.reason instanceof ApiError &&
          (t.reason.code === 'NO_PUZZLE' || t.reason.status === 404)
        ) {
          setNoPuzzle(true);
        }
        if (bw.status === 'fulfilled') setBatchWar(bw.value);
        else setBatchWar(null);
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, [game]);

  const status = today?.status || 'NOT_STARTED';
  const done = status === 'SOLVED' || status === 'FAILED';
  const playTo = game === 'wordle' ? '/play' : `/play?game=${game}`;

  const meta =
    game === 'connections'
      ? today && (
          <p className="hero__meta">
            {today.config?.groupCount || 4} groups ·{' '}
            {today.config?.maxMistakes || 4} mistakes
            {today.difficulty != null && <> · difficulty {today.difficulty}/5</>}
          </p>
        )
      : today && (
          <p className="hero__meta">
            {today.config?.wordLength || 5} letters ·{' '}
            {today.config?.maxGuesses || 6} guesses
            {today.difficulty != null && <> · difficulty {today.difficulty}/5</>}
          </p>
        );

  return (
    <div className="page page--home">
      <header className="home-header">
        <h1 className="logo logo--sm">
          <span className="logo__bit">8</span>BIT
        </h1>
        <span className="home-hello">Hi, {user?.username || 'player'}</span>
      </header>

      <div className="segmented" role="tablist" aria-label="Choose game">
        {GAMES.map((g) => (
          <button
            key={g.type}
            role="tab"
            aria-selected={g.type === game}
            className={'seg' + (g.type === game ? ' seg--active' : '')}
            onClick={() => setGame(g.type)}
          >
            {g.label}
          </button>
        ))}
      </div>

      <BatchWarBar data={batchWar} />

      <section className="hero">
        <div className="hero__badge">{today?.date || 'Today'}</div>
        <h2 className="hero__title">{TITLES[game]}</h2>

        {loading ? (
          <p className="hero__meta">Loading…</p>
        ) : noPuzzle ? (
          <>
            <p className="hero__meta">No {TITLES[game]} puzzle today.</p>
            {game !== 'wordle' && (
              <Link className="btn btn--primary btn--block btn--lg" to="/play">
                Play Wordle instead
              </Link>
            )}
          </>
        ) : (
          <>
            {meta}
            <span
              className={
                'status-chip status-chip--' + status.toLowerCase().replace('_', '-')
              }
            >
              {status === 'SOLVED'
                ? '✓ Solved'
                : status === 'FAILED'
                ? '✗ Failed'
                : status === 'IN_PROGRESS'
                ? '… In progress'
                : '● Not started'}
            </span>

            <Link className="btn btn--primary btn--block btn--lg" to={playTo}>
              {STATUS_CTA[status] || 'Play'}
            </Link>

            {done && today?.score != null && (
              <p className="hero__score">Score: {today.score}</p>
            )}
          </>
        )}
      </section>

      <div className="home-links">
        <Link className="card-link" to="/leaderboard">
          ★ Leaderboard
        </Link>
        <Link className="card-link" to="/profile">
          ☻ My profile
        </Link>
      </div>
    </div>
  );
}
