// Renders the Connections board: solved rows on top, then the 4x4 grid of
// remaining selectable tiles. Colours by level but always shows the category
// label so it stays colourblind-safe.

function SolvedRow({ group }) {
  const level = group.level ?? 0;
  return (
    <div className={`conn-solved conn-solved--l${level}`}>
      <div className="conn-solved__cat">{group.category}</div>
      <div className="conn-solved__members">
        {(group.members || []).join(' · ')}
      </div>
    </div>
  );
}

export default function ConnectionsBoard({
  tiles = [], // remaining (unsolved) tile strings, already ordered for display
  solvedGroups = [], // [{level, category, members:[4]}]
  selected = [], // currently selected tile strings
  onToggle, // (tile) => void
  shakeSelected = false, // shake the selected tiles (wrong guess)
  popOut = [], // tiles animating out (just-solved)
  disabled = false,
}) {
  return (
    <div className="conn">
      {solvedGroups.length > 0 && (
        <div className="conn-solved-stack">
          {solvedGroups
            .slice()
            .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
            .map((g) => (
              <SolvedRow key={g.level ?? g.category} group={g} />
            ))}
        </div>
      )}

      <div className="conn-grid" role="grid" aria-label="Connections board">
        {tiles.map((tile) => {
          const isSel = selected.includes(tile);
          const isOut = popOut.includes(tile);
          const cls =
            'conn-tile' +
            (isSel ? ' conn-tile--selected' : '') +
            (isSel && shakeSelected ? ' conn-tile--shake' : '') +
            (isOut ? ' conn-tile--out' : '');
          return (
            <button
              key={tile}
              type="button"
              className={cls}
              aria-pressed={isSel}
              disabled={disabled}
              onClick={() => onToggle && onToggle(tile)}
            >
              <span className="conn-tile__word">{tile}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
