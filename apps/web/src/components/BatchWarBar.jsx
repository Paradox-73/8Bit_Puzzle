// Batch War: each cohort's participation % (distinct solvers ÷ batch capacity) for today.
// Normalised by capacity so a small batch isn't out-muscled by a big one — getting more of
// your batch to play is what moves the bar.
//
// Collapsible: with one row per programme×year the full list can run long, so the section can
// be minimised to just the header (leader + your standing). The choice is remembered per browser.

import { useState } from 'react';

const COLLAPSE_KEY = '8bit.batchwar.collapsed';
// Below this many cohorts the list is short enough to show open by default.
const AUTO_COLLAPSE_ABOVE = 5;

export default function BatchWarBar({ data }) {
  const cohorts = data && Array.isArray(data.cohorts) ? data.cohorts : [];

  // Default: open for a short list, minimised for a long one — unless the user has chosen.
  const [collapsed, setCollapsed] = useState(() => {
    const saved = localStorage.getItem(COLLAPSE_KEY);
    if (saved === 'true') return true;
    if (saved === 'false') return false;
    return cohorts.length > AUTO_COLLAPSE_ABOVE;
  });

  if (cohorts.length === 0) return null;

  const leader = data.leader;
  const anyPlayed = cohorts.some((c) => (c.solvers || 0) > 0);

  const toggle = () => {
    setCollapsed((c) => {
      const next = !c;
      localStorage.setItem(COLLAPSE_KEY, String(next));
      return next;
    });
  };

  return (
    <section className="batchwar" aria-label="Batch war standings">
      <button
        type="button"
        className="batchwar__head batchwar__head--toggle"
        onClick={toggle}
        aria-expanded={!collapsed}
      >
        <span className="batchwar__title">⚔ BATCH WAR</span>
        <span className="batchwar__leader">
          {leader ? `Leading: ${leader}` : 'No solves yet'}
        </span>
        <span className="batchwar__chevron" aria-hidden="true">
          {collapsed ? '▸' : '▾'}
        </span>
      </button>

      {!collapsed && (
        <>
          <div className="batchwar__rows">
            {cohorts.map((c) => (
              <div className="bw-row" key={c.label}>
                <span className="bw-row__label">{c.label}</span>
                <div className="bw-row__track">
                  <div
                    className={'bw-row__bar' + (c.label === leader ? ' bw-row__bar--leader' : '')}
                    style={{ width: `${Math.max(6, c.pct || 0)}%` }}
                  >
                    <span className="bw-row__pct">{c.pct || 0}%</span>
                  </div>
                </div>
                <span className="bw-row__count" title="solvers / batch size">
                  {c.solvers || 0}/{c.capacity}
                </span>
              </div>
            ))}
          </div>

          <p className="batchwar__nudge">
            {anyPlayed
              ? 'Every batchmate who solves lifts your % — go drag them in.'
              : 'Be the first to solve for your batch!'}
          </p>
        </>
      )}
    </section>
  );
}
