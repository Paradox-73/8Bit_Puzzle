import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import PushToggle from '../components/PushToggle.jsx';

function StatBox({ label, value }) {
  return (
    <div className="stat-box">
      <div className="stat-box__value">{value}</div>
      <div className="stat-box__label">{label}</div>
    </div>
  );
}

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const { toast } = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    api
      .me()
      .then((d) => alive && setData(d))
      .catch((err) => toast(err.message || 'Failed to load profile', { type: 'error' }))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [toast]);

  const stats = data?.stats || {};
  const u = data?.user || user || {};
  const winPct =
    stats.winRate != null
      ? Math.round(stats.winRate * (stats.winRate <= 1 ? 100 : 1))
      : null;

  return (
    <div className="page page--profile">
      <header className="profile-head">
        <div className="avatar" aria-hidden="true">
          {(u.username || '?').slice(0, 1).toUpperCase()}
        </div>
        <div>
          <h1 className="profile-name">{u.username || 'You'}</h1>
          <p className="profile-meta">
            {u.program} · Batch {u.batchYear} · {u.rollNumber}
          </p>
        </div>
      </header>

      {loading ? (
        <div className="loading">Loading…</div>
      ) : (
        <>
          <section className="streak-banner">
            <div className="streak-banner__main">
              🔥 <span className="streak-banner__num">{stats.currentStreak ?? 0}</span>
              <span className="streak-banner__cap"> day streak</span>
            </div>
            <div className="streak-banner__best">
              Best: {stats.bestStreak ?? 0} 🔥
            </div>
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

          <section className="settings">
            <h2 className="section-title">Notifications</h2>
            <PushToggle />
          </section>

          <button className="btn btn--ghost btn--block logout-btn" onClick={logout}>
            Log out
          </button>
        </>
      )}
    </div>
  );
}
