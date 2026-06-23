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

const GAMES = [
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
  { type: 'cryptic', label: 'Cryptic' },
];

// Group difficulty colours, easiest → hardest (matches the in-game scheme).
const CONN_COLORS = ['🟡 Yellow · easiest', '🟢 Green', '🔵 Blue', '🟣 Purple · hardest'];
const CRYPTIC_FIELDS = [
  ['answer', 'Answer', 'LISTEN', false],
  ['enumeration', 'Enumeration', '(6)', false],
  ['clue', 'Clue', 'Pay attention to broken tinsel (6)', true],
  ['definition', 'Definition', 'Pay attention', false],
  ['indicator', 'Indicator', 'broken', false],
  ['fodder', 'Fodder', 'tinsel', false],
  ['device', 'Device', 'Anagram', false],
  ['explanation', 'Explanation', 'LISTEN is an anagram (broken) of TINSEL.', true],
];

// Blank content for a brand-new puzzle of the given game.
function emptyContent(game) {
  if (game === 'connections') {
    return { groups: [0, 1, 2, 3].map((lvl) => ({ level: lvl, category: '', members: ['', '', '', ''] })) };
  }
  if (game === 'cryptic') {
    return Object.fromEntries(CRYPTIC_FIELDS.map(([k]) => [k, '']));
  }
  return { answer: '' }; // wordle
}
const emptyForm = (game) => ({ id: null, difficulty: 2, publishDate: '', status: null, content: emptyContent(game) });

// Merge a saved puzzle's content into the editable shape (fills any missing fields).
function hydrateContent(game, content) {
  const c = content || {};
  if (game === 'connections') {
    const groups = Array.isArray(c.groups) ? c.groups : [];
    return {
      groups: [0, 1, 2, 3].map((i) => {
        const g = groups[i] || {};
        const members = Array.isArray(g.members) ? g.members : [];
        return {
          level: g.level ?? i,
          category: g.category || '',
          members: [0, 1, 2, 3].map((j) => members[j] || ''),
        };
      }),
    };
  }
  if (game === 'cryptic') {
    return Object.fromEntries(
      CRYPTIC_FIELDS.map(([k]) => [k, k === 'answer' ? (c[k] || '').toUpperCase() : c[k] || ''])
    );
  }
  return { answer: (c.answer || '').toUpperCase() };
}

// Build + validate the content payload. Throws a user-facing string on invalid input.
function buildContent(game, content) {
  if (game === 'wordle') {
    const answer = (content.answer || '').trim().toUpperCase();
    if (!/^[A-Z]{5}$/.test(answer)) throw 'Wordle answer must be exactly 5 letters';
    return { answer };
  }
  if (game === 'connections') {
    const seen = new Set();
    const groups = content.groups.map((g, i) => {
      const category = (g.category || '').trim();
      if (!category) throw `Group ${i + 1}: add a category name`;
      const members = g.members.map((w) => (w || '').trim().toUpperCase());
      if (members.some((w) => !w)) throw `Group ${i + 1}: fill in all 4 words`;
      members.forEach((w) => {
        if (seen.has(w)) throw `Duplicate word across groups: ${w}`;
        seen.add(w);
      });
      return { level: Number(g.level), category, members };
    });
    return { groups };
  }
  // cryptic
  const out = {};
  for (const [k, label] of CRYPTIC_FIELDS) {
    const v = (content[k] || '').trim();
    if (!v) throw `Cryptic: ${label} is required`;
    out[k] = k === 'answer' ? v.toUpperCase() : v;
  }
  return out;
}

export default function AdminPage() {
  const { toast } = useToast();
  const [game, setGame] = useState('wordle');
  const [month, setMonth] = useState(() => ymOf(new Date()));
  const [calendar, setCalendar] = useState(null);
  const [puzzles, setPuzzles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(() => emptyForm('wordle'));
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [cal, list] = await Promise.allSettled([
        api.adminCalendar({ type: game, month }),
        api.adminPuzzles({ type: game, month }),
      ]);
      if (cal.status === 'fulfilled') setCalendar(cal.value);
      if (list.status === 'fulfilled') setPuzzles(list.value || []);
    } catch (err) {
      toast(err.message || 'Failed to load admin data', { type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [game, month, toast]);

  useEffect(() => {
    reload();
  }, [reload]);

  const switchGame = (g) => {
    if (g === game) return;
    setGame(g);
    setForm(emptyForm(g));
  };

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
      difficulty: p.difficulty ?? 2,
      publishDate: p.publishDate || '',
      status: p.status || null,
      content: hydrateContent(game, p.content),
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const newPuzzleFor = (date) => {
    setForm({ ...emptyForm(game), publishDate: date });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const onDayClick = (date) => {
    const p = puzzleByDate[date];
    if (p) editPuzzle(p);
    else newPuzzleFor(date);
  };

  // ---- content field updaters ----
  const setField = (key, val) =>
    setForm((f) => ({
      ...f,
      content: { ...f.content, [key]: key === 'answer' ? val.toUpperCase() : val },
    }));
  const setGroupCategory = (gi, val) =>
    setForm((f) => ({
      ...f,
      content: { ...f.content, groups: f.content.groups.map((g, i) => (i === gi ? { ...g, category: val } : g)) },
    }));
  const setGroupMember = (gi, mi, val) =>
    setForm((f) => ({
      ...f,
      content: {
        ...f.content,
        groups: f.content.groups.map((g, i) =>
          i === gi ? { ...g, members: g.members.map((w, j) => (j === mi ? val.toUpperCase() : w)) } : g
        ),
      },
    }));

  const saveForm = async (e) => {
    e.preventDefault();
    if (saving) return;
    if (!form.publishDate) {
      toast('Pick a publish date', { type: 'warn' });
      return;
    }
    let content;
    try {
      content = buildContent(game, form.content);
    } catch (msg) {
      toast(typeof msg === 'string' ? msg : 'Check the puzzle fields', { type: 'warn', duration: 3000 });
      return;
    }
    setSaving(true);
    try {
      if (form.id) {
        await api.adminUpdatePuzzle(form.id, {
          publishDate: form.publishDate,
          difficulty: Number(form.difficulty),
          content,
        });
        toast('Puzzle updated', { type: 'success' });
      } else {
        const created = await api.adminCreatePuzzle({
          gameType: game,
          publishDate: form.publishDate,
          difficulty: Number(form.difficulty),
          content,
          easterEggs: null,
        });
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
      setForm(emptyForm(game));
      await reload();
    } catch (err) {
      toast(err.message || 'Delete failed', { type: 'error' });
    }
  };

  const gameLabel = GAMES.find((g) => g.type === game)?.label || 'Wordle';

  return (
    <div className="page page--admin">
      <h1 className="page-title">⚙ Admin · {gameLabel}</h1>

      <div className="segmented" role="tablist" aria-label="Choose game to manage">
        {GAMES.map((g) => (
          <button
            key={g.type}
            role="tab"
            aria-selected={g.type === game}
            className={'seg' + (g.type === game ? ' seg--active' : '')}
            onClick={() => switchGame(g.type)}
          >
            {g.label}
          </button>
        ))}
      </div>

      {bufferWarn && (
        <div className="banner banner--warn">
          ⚠ Only {calendar.bufferDays} day{calendar.bufferDays === 1 ? '' : 's'} of {gameLabel} puzzles
          queued — schedule more!
        </div>
      )}

      {/* Puzzle form */}
      <section className="admin-form-wrap">
        <h2 className="section-title">{form.id ? `Edit puzzle #${form.id}` : `New ${gameLabel} puzzle`}</h2>
        <form className="form" onSubmit={saveForm}>
          {game === 'wordle' && (
            <label className="field">
              <span className="field__label">Answer word (5 letters)</span>
              <input
                className="input input--mono input--upper"
                value={form.content.answer}
                onChange={(e) => setField('answer', e.target.value)}
                maxLength={5}
                placeholder="PIXEL"
              />
            </label>
          )}

          {game === 'connections' && (
            <div className="conn-editor">
              {form.content.groups.map((g, gi) => (
                <fieldset key={gi} className="conn-group">
                  <legend className="conn-group__legend">{CONN_COLORS[gi]}</legend>
                  <input
                    className="input"
                    placeholder="Category (e.g. Hostel life)"
                    value={g.category}
                    onChange={(e) => setGroupCategory(gi, e.target.value)}
                  />
                  <div className="conn-words">
                    {g.members.map((w, mi) => (
                      <input
                        key={mi}
                        className="input input--mono input--upper"
                        placeholder={`Word ${mi + 1}`}
                        value={w}
                        onChange={(e) => setGroupMember(gi, mi, e.target.value)}
                        maxLength={16}
                      />
                    ))}
                  </div>
                </fieldset>
              ))}
            </div>
          )}

          {game === 'cryptic' && (
            <div className="cryptic-editor">
              {CRYPTIC_FIELDS.map(([key, label, ph, multiline]) => (
                <label className="field" key={key}>
                  <span className="field__label">{label}</span>
                  {multiline ? (
                    <textarea
                      className="input"
                      rows={2}
                      value={form.content[key]}
                      onChange={(e) => setField(key, e.target.value)}
                      placeholder={ph}
                    />
                  ) : (
                    <input
                      className={'input' + (key === 'answer' ? ' input--mono input--upper' : '')}
                      value={form.content[key]}
                      onChange={(e) => setField(key, e.target.value)}
                      placeholder={ph}
                      maxLength={key === 'answer' ? 20 : undefined}
                    />
                  )}
                </label>
              ))}
            </div>
          )}

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
                <button type="button" className="btn btn--ghost" onClick={() => setForm(emptyForm(game))}>
                  New
                </button>
              </>
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
                    {status && <span className="cal-cell__status">{String(status).slice(0, 3)}</span>}
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
