import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api.js';

function StatBox({ label, value }) {
  return (
    <div className="stat-box">
      <div className="stat-box__value">{value}</div>
      <div className="stat-box__label">{label}</div>
    </div>
  );
}

export default function UserPage() {
  const { username } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    api
      .user(username)
      .then((d) => alive && setData(d))
      .catch((err) => alive && setError(err.message || 'User not found'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [username]);

  const stats = data?.stats || {};
  const winPct =
    stats.winRate != null
      ? Math.round(stats.winRate * (stats.winRate <= 1 ? 100 : 1))
      : null;

  return (
    <div className="page page--profile">
      <p>
        <Link className="back-link" to="/leaderboard">
          ← Back
        </Link>
      </p>

      {loading ? (
        <div className="loading">Loading…</div>
      ) : error ? (
        <div className="empty">{error}</div>
      ) : (
        <>
          <header className="profile-head">
            <div className="avatar" aria-hidden="true">
              {(data.username || '?').slice(0, 1).toUpperCase()}
            </div>
            <div>
              <h1 className="profile-name">{data.username}</h1>
              <p className="profile-meta">
                {data.program} · Batch {data.batchYear}
              </p>
            </div>
          </header>

          <section className="streak-banner">
            <div className="streak-banner__main">
              🔥 <span className="streak-banner__num">{stats.currentStreak ?? 0}</span>
              <span className="streak-banner__cap"> day streak</span>
            </div>
            <div className="streak-banner__best">Best: {stats.bestStreak ?? 0} 🔥</div>
          </section>

          <section className="stats-grid">
            <StatBox label="Win %" value={winPct != null ? `${winPct}%` : '—'} />
            <StatBox label="Played" value={stats.totalPlayed ?? 0} />
            <StatBox label="Solved" value={stats.totalSolved ?? 0} />
          </section>

          {Array.isArray(stats.titles) && stats.titles.length > 0 && (
            <section className="titles">
              <h2 className="section-title">Titles</h2>
              <div className="badges">
                {stats.titles.map((t) => (
                  <span key={t} className="badge">
                    🏅 {t}
                  </span>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
