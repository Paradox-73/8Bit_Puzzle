// Pre-launch playtest dashboard (editors only): solve-rates, timings, ratings, per-player progress
// and feedback over trial attempts — with a Live tab to see how the juniors actually play after
// launch (kept separate). Plus export-to-file and a purge. Self-contained: delete this file + its
// import/usage in AdminPage to remove.

import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { useToast } from './Toast.jsx';

function fmtMs(ms) {
  if (ms == null) return '—';
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  return `${Math.floor(s / 60)}m ${s % 60}s`;
}

const GAME_LABEL = { wordle: 'Wordle', connections: 'Connections', cryptic: 'Cryptic' };

export default function TrialStats() {
  const { toast } = useToast();
  const [scope, setScope] = useState('trial'); // 'trial' (playtest) | 'live' (real junior play)
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStats(await api.adminTrialStats(scope));
    } catch (err) {
      toast(err.message || 'Failed to load stats', { type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [scope, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (key, fn, done) => {
    setBusy(key);
    try {
      done(await fn());
      await load();
    } catch (err) {
      toast(err.message || 'Action failed', { type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const sync = () =>
    run('sync', api.adminTrialSync, (r) => toast(`Synced ${r.puzzles} trial puzzles`, { type: 'success' }));
  const exportStats = () =>
    run('export', api.adminTrialExport, (r) =>
      toast(r.error ? `Export failed: ${r.error}` : `Exported (${r.snapshots} snapshots) → ${r.path}`,
        { type: r.error ? 'error' : 'success', duration: 5000 }));
  const reset = () => {
    if (!window.confirm('Full teardown: export stats, then delete ALL trial attempts + puzzles. Use before launch.')) return;
    run('reset', api.adminTrialReset, (r) =>
      toast(`Exported, then deleted ${r.attemptsDeleted} attempts + ${r.puzzlesDeleted} puzzles`,
        { type: 'success', duration: 5000 }));
  };

  const isTrial = scope === 'trial';
  const empty = !stats || stats.totalAttempts === 0;

  return (
    <section className="admin-trial">
      <div className="admin-trial__head">
        <h2 className="section-title">{isTrial ? '🧪 Trial stats' : '🎮 Live play stats'}</h2>
        <div className="admin-trial__actions">
          <div className="segmented">
            <button
              className={'seg' + (isTrial ? ' seg--active' : '')}
              onClick={() => setScope('trial')}
            >
              Trial
            </button>
            <button
              className={'seg' + (!isTrial ? ' seg--active' : '')}
              onClick={() => setScope('live')}
            >
              Live (juniors)
            </button>
          </div>
          <button className="btn btn--small" onClick={load} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
          {isTrial && (
            <>
              <button className="btn btn--small" onClick={sync} disabled={busy === 'sync'}>
                {busy === 'sync' ? 'Syncing…' : 'Sync puzzles'}
              </button>
              <button className="btn btn--small" onClick={exportStats} disabled={busy === 'export'}>
                {busy === 'export' ? 'Exporting…' : 'Export stats'}
              </button>
              <button className="btn btn--small btn--danger" onClick={reset} disabled={busy === 'reset'}>
                {busy === 'reset' ? 'Resetting…' : 'Reset trial data'}
              </button>
            </>
          )}
        </div>
      </div>

      {loading ? (
        <div className="loading">Loading stats…</div>
      ) : empty ? (
        <p className="empty">
          {isTrial
            ? 'No trial data yet. Turn on trial mode (TRIAL_ENABLED) and have testers play.'
            : 'No live play yet — this fills up once the real games start.'}
        </p>
      ) : (
        <>
          <div className="admin-trial__summary">
            <span><strong>{stats.players}</strong> players</span>
            <span><strong>{stats.totalAttempts}</strong> attempts</span>
            <span><strong>{stats.solveRate}%</strong> solved</span>
          </div>

          <h3 className="admin-trial__sub">By game &amp; difficulty</h3>
          <table className="admin-trial__table">
            <thead>
              <tr>
                <th>Game</th><th>Diff</th><th>Plays</th><th>Solved</th>
                <th>Avg time</th><th>Avg guesses</th>{isTrial && <th>Rating</th>}
              </tr>
            </thead>
            <tbody>
              {stats.byDifficulty.map((d, i) => (
                <tr key={i}>
                  <td>{GAME_LABEL[d.gameType] || d.gameType}</td>
                  <td>{d.difficulty ?? '—'}</td>
                  <td>{d.attempts}</td>
                  <td>{d.solveRate}%</td>
                  <td>{fmtMs(d.avgMs)}</td>
                  <td>{d.avgGuesses ?? '—'}</td>
                  {isTrial && <td>{d.avgRating != null ? `${d.avgRating}★` : '—'}</td>}
                </tr>
              ))}
            </tbody>
          </table>

          <h3 className="admin-trial__sub">By player</h3>
          <table className="admin-trial__table">
            <thead>
              <tr><th>Player</th><th>Roll</th><th>Plays</th><th>Solved</th><th>Total time</th></tr>
            </thead>
            <tbody>
              {stats.byPlayer.map((p) => (
                <tr key={p.userId}>
                  <td>{p.username || `#${p.userId}`}</td>
                  <td>{p.rollNumber || '—'}</td>
                  <td>{p.attempts}</td>
                  <td>{p.solveRate}%</td>
                  <td>{fmtMs(p.totalMs)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {isTrial && stats.comments && stats.comments.length > 0 && (
            <>
              <h3 className="admin-trial__sub">Feedback &amp; change requests</h3>
              <ul className="admin-trial__comments">
                {stats.comments.map((c, i) => (
                  <li key={i}>
                    <span className="admin-trial__cmeta">
                      {c.rating ? `${c.rating}★ ` : ''}
                      {GAME_LABEL[c.gameType] || c.gameType} {c.date} · {c.username || '—'}
                    </span>
                    <span className="admin-trial__ctext">{c.message}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}
