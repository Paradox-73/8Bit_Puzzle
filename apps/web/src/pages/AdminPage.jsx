import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, ApiError } from '../api.js';
import { useToast } from '../components/Toast.jsx';

// ---- date helpers (local, no deps) ----
function ymOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}
function shiftMonth(ym, delta) {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return ymOf(d);
}
function daysInMonthGrid(ym) {
  const [y, m] = ym.split('-').map(Number);
  const first = new Date(y, m - 1, 1);
  const startWeekday = first.getDay(); // 0=Sun
  const numDays = new Date(y, m, 0).getDate();
  const cells = [];
  for (let i = 0; i < startWeekday; i++) cells.push(null);
  for (let d = 1; d <= numDays; d++) {
    cells.push(`${ym}-${String(d).padStart(2, '0')}`);
  }
  return cells;
}

const EMPTY_FORM = { id: null, answer: '', difficulty: 2, publishDate: '', status: null };

export default function AdminPage() {
  const { toast } = useToast();
  const [month, setMonth] = useState(() => ymOf(new Date()));
  const [calendar, setCalendar] = useState(null);
  const [puzzles, setPuzzles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [cal, list] = await Promise.allSettled([
        api.adminCalendar({ type: 'wordle', month }),
        api.adminPuzzles({ type: 'wordle', month }),
      ]);
      if (cal.status === 'fulfilled') setCalendar(cal.value);
      if (list.status === 'fulfilled') setPuzzles(list.value || []);
    } catch (err) {
      toast(err.message || 'Failed to load admin data', { type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [month, toast]);

  useEffect(() => {
    reload();
  }, [reload]);

  const gapSet = useMemo(() => new Set(calendar?.gaps || []), [calendar]);
  const dayByDate = useMemo(() => {
    const map = {};
    (calendar?.days || []).forEach((d) => {
      map[d.date] = d;
    });
    return map;
  }, [calendar]);
  const puzzleByDate = useMemo(() => {
    const map = {};
    puzzles.forEach((p) => {
      if (p.publishDate) map[p.publishDate] = p;
    });
    return map;
  }, [puzzles]);

  const bufferWarn =
    calendar &&
    calendar.bufferDays != null &&
    calendar.warnBelow != null &&
    calendar.bufferDays < calendar.warnBelow;

  const cells = daysInMonthGrid(month);

  const editPuzzle = (p) => {
    setForm({
      id: p.id,
      answer: (p.content?.answer || '').toUpperCase(),
      difficulty: p.difficulty ?? 2,
      publishDate: p.publishDate || '',
      status: p.status || null,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const newPuzzleFor = (date) => {
    setForm({ ...EMPTY_FORM, publishDate: date });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const onDayClick = (date) => {
    const p = puzzleByDate[date];
    if (p) editPuzzle(p);
    else newPuzzleFor(date);
  };

  const saveForm = async (e) => {
    e.preventDefault();
    if (saving) return;
    const answer = form.answer.trim().toUpperCase();
    if (!answer || !/^[A-Z]+$/.test(answer)) {
      toast('Enter a valid word (letters only)', { type: 'warn' });
      return;
    }
    if (!form.publishDate) {
      toast('Pick a publish date', { type: 'warn' });
      return;
    }
    setSaving(true);
    try {
      const payload = {
        gameType: 'wordle',
        publishDate: form.publishDate,
        difficulty: Number(form.difficulty),
        content: { answer },
      };
      if (form.id) {
        await api.adminUpdatePuzzle(form.id, {
          publishDate: form.publishDate,
          difficulty: Number(form.difficulty),
          content: { answer },
        });
        toast('Puzzle updated', { type: 'success' });
      } else {
        const created = await api.adminCreatePuzzle({ ...payload, easterEggs: null });
        toast('Draft created', { type: 'success' });
        if (created?.id) setForm((f) => ({ ...f, id: created.id, status: created.status }));
      }
      await reload();
    } catch (err) {
      toast(err.message || 'Save failed', { type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  const runAction = async (fn, okMsg) => {
    if (!form.id) return;
    try {
      await fn(form.id);
      toast(okMsg, { type: 'success' });
      await reload();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        toast(err.message || 'Action blocked (conflict)', { type: 'warn', duration: 3500 });
      } else {
        toast(err.message || 'Action failed', { type: 'error' });
      }
    }
  };

  const removePuzzle = async () => {
    if (!form.id) return;
    try {
      await api.adminDeletePuzzle(form.id);
      toast('Puzzle deleted', { type: 'info' });
      setForm(EMPTY_FORM);
      await reload();
    } catch (err) {
      toast(err.message || 'Delete failed', { type: 'error' });
    }
  };

  return (
    <div className="page page--admin">
      <h1 className="page-title">⚙ Admin · Wordle</h1>

      {bufferWarn && (
        <div className="banner banner--warn">
          ⚠ Only {calendar.bufferDays} day{calendar.bufferDays === 1 ? '' : 's'} of puzzles
          queued — schedule more!
        </div>
      )}

      {/* Puzzle form */}
      <section className="admin-form-wrap">
        <h2 className="section-title">{form.id ? `Edit puzzle #${form.id}` : 'New puzzle'}</h2>
        <form className="form" onSubmit={saveForm}>
          <label className="field">
            <span className="field__label">Answer word</span>
            <input
              className="input input--mono input--upper"
              value={form.answer}
              onChange={(e) => setForm((f) => ({ ...f, answer: e.target.value.toUpperCase() }))}
              maxLength={12}
              placeholder="PIXEL"
            />
          </label>

          <label className="field">
            <span className="field__label">Difficulty: {form.difficulty}/5</span>
            <input
              type="range"
              min="1"
              max="5"
              step="1"
              value={form.difficulty}
              onChange={(e) => setForm((f) => ({ ...f, difficulty: Number(e.target.value) }))}
            />
          </label>

          <label className="field">
            <span className="field__label">Publish date</span>
            <input
              type="date"
              className="input"
              value={form.publishDate}
              onChange={(e) => setForm((f) => ({ ...f, publishDate: e.target.value }))}
            />
          </label>

          {form.status && (
            <div className="form-status">
              Status: <strong>{form.status}</strong>
            </div>
          )}

          <div className="admin-actions">
            <button className="btn btn--primary" disabled={saving}>
              {saving ? 'Saving…' : form.id ? 'Save changes' : 'Create draft'}
            </button>
            {form.id && (
              <>
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => runAction(api.adminSubmitReview, 'Submitted for review')}
                >
                  Submit review
                </button>
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => runAction(api.adminApprove, 'Approved')}
                >
                  Approve
                </button>
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => runAction(api.adminSchedule, 'Scheduled')}
                >
                  Schedule
                </button>
                <button type="button" className="btn btn--danger" onClick={removePuzzle}>
                  Delete
                </button>
              </>
            )}
            {form.id && (
              <button
                type="button"
                className="btn btn--ghost"
                onClick={() => setForm(EMPTY_FORM)}
              >
                New
              </button>
            )}
          </div>
        </form>
      </section>

      {/* Calendar */}
      <section className="admin-cal">
        <div className="admin-cal__head">
          <button className="btn btn--small" onClick={() => setMonth((m) => shiftMonth(m, -1))}>
            ‹
          </button>
          <span className="admin-cal__month">{month}</span>
          <button className="btn btn--small" onClick={() => setMonth((m) => shiftMonth(m, 1))}>
            ›
          </button>
        </div>

        {loading ? (
          <div className="loading">Loading calendar…</div>
        ) : (
          <>
            <div className="cal-weekdays">
              {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((d, i) => (
                <span key={i}>{d}</span>
              ))}
            </div>
            <div className="cal-grid">
              {cells.map((date, i) => {
                if (!date) return <div key={`pad-${i}`} className="cal-cell cal-cell--pad" />;
                const day = dayByDate[date];
                const isGap = gapSet.has(date);
                const status = day?.status;
                const diff = day?.difficulty;
                const dayNum = Number(date.slice(-2));
                return (
                  <button
                    key={date}
                    className={
                      'cal-cell' +
                      (isGap ? ' cal-cell--gap' : '') +
                      (status ? ` cal-cell--${String(status).toLowerCase()}` : '')
                    }
                    onClick={() => onDayClick(date)}
                    title={isGap ? 'Gap — no puzzle' : status || 'No puzzle'}
                  >
                    <span className="cal-cell__num">{dayNum}</span>
                    {diff != null && <span className="cal-cell__diff">{'•'.repeat(diff)}</span>}
                    {status && (
                      <span className="cal-cell__status">{String(status).slice(0, 3)}</span>
                    )}
                    {isGap && <span className="cal-cell__gap">!</span>}
                  </button>
                );
              })}
            </div>
            <div className="cal-legend">
              <span className="cal-legend__item cal-legend__item--gap">Gap day</span>
              <span className="cal-legend__item">• = difficulty</span>
              <span>Tap a day to create / edit</span>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
