// Batch War: each cohort's participation % (distinct solvers ÷ batch capacity) for today.
// Normalised by capacity so a small batch isn't out-muscled by a big one — getting more of
// your batch to play is what moves the bar.

export default function BatchWarBar({ data }) {
  if (!data || !Array.isArray(data.cohorts) || data.cohorts.length === 0) {
    return null;
  }

  const cohorts = data.cohorts;
  const leader = data.leader;
  const anyPlayed = cohorts.some((c) => (c.solvers || 0) > 0);

  return (
    <section className="batchwar" aria-label="Batch war standings">
      <div className="batchwar__head">
        <span className="batchwar__title">⚔ BATCH WAR</span>
        <span className="batchwar__leader">
          {leader ? `Leading: ${leader}` : 'No solves yet'}
        </span>
      </div>

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
    </section>
  );
}
