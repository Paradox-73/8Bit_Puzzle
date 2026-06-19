// Tug-of-war horizontal bar split by each batch's avgScore.

export default function BatchWarBar({ data }) {
  if (!data || !Array.isArray(data.batches) || data.batches.length === 0) {
    return null;
  }

  const batches = [...data.batches].sort((a, b) => a.batchYear - b.batchYear);
  const total = batches.reduce((sum, b) => sum + (b.avgScore || 0), 0) || 1;
  const leader = data.leader;

  return (
    <section className="batchwar" aria-label="Batch war standings">
      <div className="batchwar__head">
        <span className="batchwar__title">⚔ BATCH WAR</span>
        {leader != null && (
          <span className="batchwar__leader">Leading: ’{String(leader).slice(-2)}</span>
        )}
      </div>

      <div className="batchwar__bar" role="img" aria-label="Tug of war bar by average score">
        {batches.map((b) => {
          const pct = Math.max(4, ((b.avgScore || 0) / total) * 100);
          const isLeader = b.batchYear === leader;
          return (
            <div
              key={b.batchYear}
              className={'batchwar__seg' + (isLeader ? ' batchwar__seg--leader' : '')}
              style={{ width: `${pct}%` }}
              title={`Batch ${b.batchYear}: avg ${b.avgScore}, ${b.players} players`}
            >
              <span className="batchwar__seg-label">’{String(b.batchYear).slice(-2)}</span>
            </div>
          );
        })}
      </div>

      <div className="batchwar__legend">
        {batches.map((b) => (
          <span key={b.batchYear} className="batchwar__legend-item">
            <span className="batchwar__dot" aria-hidden="true" />
            Batch {b.batchYear}: <strong>{b.avgScore}</strong> avg ({b.players}p)
          </span>
        ))}
      </div>
    </section>
  );
}
