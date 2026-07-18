import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import PushToggle from '../components/PushToggle.jsx';
import ThemeToggle from '../components/ThemeToggle.jsx';
import FeedbackModal from '../components/FeedbackModal.jsx';
import { useInstallPrompt } from '../pwa.js';

function StatBox({ label, value }) {
  return (
    <div className="stat-box">
      <div className="stat-box__value">{value}</div>
      <div className="stat-box__label">{label}</div>
    </div>
  );
}

// Per-game distribution: Wordle/Cryptic by guesses-to-solve (1–6), Connections by mistakes (0–4).
const DIST_GAMES = [
  { key: 'wordle', label: 'Wordle', kind: 'guesses' },
  { key: 'connections', label: 'Connections', kind: 'mistakes' },
  { key: 'cryptic', label: 'Cryptic', kind: 'guesses' },
];

function Distributions({ distributions }) {
  const [game, setGame] = useState('wordle');
  if (!distributions) return null;
  const cfg = DIST_GAMES.find((g) => g.key === game) || DIST_GAMES[0];
  const dist = distributions[game] || [];
  const total = dist.reduce((a, b) => a + b, 0);
  const max = Math.max(...dist, 1);

  return (
    <section className="titles">
      <h2 className="section-title">Distribution</h2>
      <div className="segmented" role="tablist" aria-label="Choose game" style={{ marginBottom: 10 }}>
        {DIST_GAMES.map((g) => (
          <button
            key={g.key}
            role="tab"
            aria-selected={g.key === game}
            className={'seg' + (g.key === game ? ' seg--active' : '')}
            onClick={() => setGame(g.key)}
          >
            {g.label}
          </button>
        ))}
      </div>

      {total === 0 ? (
        <p className="profile-meta">Solve a {cfg.label} puzzle to start your distribution.</p>
      ) : (
        <>
          <div className="dist">
            {dist.map((count, i) => (
              <div className="dist-row" key={i}>
                <span className="dist-row__label">{cfg.kind === 'mistakes' ? i : i + 1}</span>
                <div className="dist-row__track">
                  <div
                    className="dist-row__bar"
                    style={{ width: `${Math.max(8, (count / max) * 100)}%` }}
                  >
                    <span className="dist-row__count">{count}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
          <p className="profile-meta">
            {cfg.kind === 'mistakes' ? 'Mistakes made on solved boards (0–4)' : 'Guesses used to solve'}
          </p>
        </>
      )}
    </section>
  );
}

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const { toast } = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [feedbackType, setFeedbackType] = useState(null); // 'feedback' | 'bug' | null
  const { installed, canPrompt, isIos, promptInstall } = useInstallPrompt();

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
          {stats.flagged && (
            <div className="warn-banner" role="alert">
              🙂 A friendly reminder to play fair and solve the puzzles yourself — it keeps the
              leaderboards fun and meaningful for everyone.
            </div>
          )}

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

          <Distributions distributions={stats.distributions} />

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

          {!installed && (
            <section className="settings">
              <h2 className="section-title">📲 Install the game</h2>
              {canPrompt ? (
                <div className="push-row">
                  <div className="push-row__text">
                    <span>Add 8Bit to your home screen</span>
                    <span className="push-row__sub">One-tap daily play, works offline.</span>
                  </div>
                  <button className="btn btn--primary" onClick={promptInstall}>
                    Install
                  </button>
                </div>
              ) : isIos ? (
                <p className="push-row__sub">
                  Tap <strong>Share</strong> <span aria-hidden="true">⎙</span> then{' '}
                  <strong>Add to Home Screen</strong> to install 8Bit.
                </p>
              ) : (
                <p className="push-row__sub">
                  Open your browser menu and choose <strong>Install app</strong> /{' '}
                  <strong>Add to Home screen</strong>.
                </p>
              )}
            </section>
          )}

          <section className="settings">
            <h2 className="section-title">Appearance</h2>
            <div className="push-row">
              <div className="push-row__text">
                <span>Theme</span>
                <span className="push-row__sub">Switch between light and dark</span>
              </div>
              <ThemeToggle />
            </div>
          </section>

          <section className="settings">
            <h2 className="section-title">Notifications</h2>
            <PushToggle />
          </section>

          <section className="settings">
            <h2 className="section-title">Help &amp; feedback</h2>
            <div className="help-links">
              <button className="btn btn--ghost btn--block" onClick={() => setFeedbackType('feedback')}>
                💡 Send feedback
              </button>
              <button className="btn btn--ghost btn--block" onClick={() => setFeedbackType('bug')}>
                🐞 Report a bug
              </button>
            </div>
          </section>

          <button className="btn btn--ghost btn--block logout-btn" onClick={logout}>
            Log out
          </button>
        </>
      )}

      {feedbackType && (
        <FeedbackModal initialType={feedbackType} onClose={() => setFeedbackType(null)} />
      )}
    </div>
  );
}
