import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import BatchWarBar from '../components/BatchWarBar.jsx';

const GAMES = [
  { type: 'all', label: 'Total' },
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
  { type: 'cryptic', label: 'Cryptic' },
];

export default function LeaderboardPage() {
  const { user } = useAuth();
  const [type, setType] = useState('all'); // all | wordle | connections | cryptic
  const [window, setWindow] = useState('daily'); // daily | alltime
  const [scope, setScope] = useState('campus'); // campus | batch
  const [board, setBoard] = useState(null);
  const [batchWar, setBatchWar] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api
      .leaderboard({ type, scope, window })
      .then((d) => alive && setBoard(d))
      .catch(() => alive && setBoard(null))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [type, scope, window]);

  useEffect(() => {
    let alive = true;
    api
      .batchWar(type)
      .then((d) => alive && setBatchWar(d))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [type]);

  const entries = board?.entries || [];

  return (
    <div className="page page--leaderboard">
      <h1 className="page-title">★ Leaderboard</h1>

      <div className="segmented lb-games" role="tablist" aria-label="Choose game">
        {GAMES.map((g) => (
          <button
            key={g.type}
            role="tab"
            aria-selected={g.type === type}
            className={'seg' + (g.type === type ? ' seg--active' : '')}
            onClick={() => setType(g.type)}
          >
            {g.label}
          </button>
        ))}
      </div>

      <BatchWarBar data={batchWar} />

      <div className="lb-filter-row">
        <div className="tabs">
          <button
            className={'tab' + (window === 'daily' ? ' tab--active' : '')}
            onClick={() => setWindow('daily')}
          >
            Today
          </button>
          <button
            className={'tab' + (window === 'alltime' ? ' tab--active' : '')}
            onClick={() => setWindow('alltime')}
          >
            All-time
          </button>
        </div>

        <div className="segmented">
          <button
            className={'seg' + (scope === 'campus' ? ' seg--active' : '')}
            onClick={() => setScope('campus')}
          >
            Campus
          </button>
          <button
            className={'seg' + (scope === 'batch' ? ' seg--active' : '')}
            onClick={() => setScope('batch')}
          >
            My Batch
          </button>
        </div>
      </div>

      {board?.me && (
        <div className="my-rank">
          Your rank: <strong>#{board.me.rank}</strong> · {board.me.score} pts
        </div>
      )}

      {loading ? (
        <div className="loading">Loading…</div>
      ) : entries.length === 0 ? (
        <div className="empty">No entries yet. Be the first!</div>
      ) : (
        <ol className="lb-list">
          {entries.map((e) => {
            const isMe = user && e.username === user.username;
            return (
              <li
                key={`${e.rank}-${e.username}`}
                className={'lb-row' + (isMe ? ' lb-row--me' : '')}
              >
                <span className="lb-rank">#{e.rank}</span>
                <Link className="lb-name" to={`/u/${encodeURIComponent(e.username)}`}>
                  {e.username}
                  {isMe && <span className="lb-you"> (you)</span>}
                </Link>
                <span className="lb-batch">’{String(e.batchYear).slice(-2)}</span>
                <span className="lb-score">{e.score}</span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
