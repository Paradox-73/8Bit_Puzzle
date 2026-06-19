// Renders the Wordle board. Shows submitted guesses (with server colours),
// the current in-progress row, and empty rows up to maxGuesses.

const SYMBOL = {
  GREEN: '✓', // correct spot
  YELLOW: '~', // wrong spot
  GREY: '·', // absent
};

const STATE_LABEL = {
  GREEN: 'correct',
  YELLOW: 'present',
  GREY: 'absent',
};

function Tile({ letter, state, flip, index, shake }) {
  const cls =
    'tile' +
    (letter ? ' tile--filled' : '') +
    (state ? ` tile--${state.toLowerCase()}` : '') +
    (flip ? ' tile--flip' : '') +
    (shake ? ' tile--shake' : '');

  return (
    <div
      className={cls}
      style={flip ? { animationDelay: `${index * 120}ms` } : undefined}
      aria-label={
        letter
          ? `${letter}${state ? ', ' + STATE_LABEL[state] : ''}`
          : 'empty'
      }
    >
      <span className="tile__letter">{letter}</span>
      {state && (
        <span className="tile__cue" aria-hidden="true">
          {SYMBOL[state]}
        </span>
      )}
    </div>
  );
}

export default function WordleGrid({
  guesses = [], // [{guess, result:[...]}]
  current = '', // current typed row text
  wordLength = 5,
  maxGuesses = 6,
  shakeRow = false, // shake the current row (invalid word)
}) {
  const rows = [];

  // Submitted rows
  for (let r = 0; r < guesses.length; r++) {
    const g = guesses[r];
    const letters = (g.guess || '').toUpperCase().split('');
    rows.push(
      <div className="grid__row" key={`done-${r}`}>
        {Array.from({ length: wordLength }).map((_, c) => (
          <Tile
            key={c}
            letter={letters[c] || ''}
            state={g.result ? g.result[c] : null}
            flip
            index={c}
          />
        ))}
      </div>
    );
  }

  // Current row (only if game still has room)
  if (guesses.length < maxGuesses) {
    const cur = (current || '').toUpperCase().split('');
    rows.push(
      <div
        className={'grid__row' + (shakeRow ? ' grid__row--shake' : '')}
        key="current"
      >
        {Array.from({ length: wordLength }).map((_, c) => (
          <Tile key={c} letter={cur[c] || ''} state={null} shake={shakeRow} />
        ))}
      </div>
    );
  }

  // Remaining empty rows
  const filled = rows.length;
  for (let r = filled; r < maxGuesses; r++) {
    rows.push(
      <div className="grid__row" key={`empty-${r}`}>
        {Array.from({ length: wordLength }).map((_, c) => (
          <Tile key={c} letter="" state={null} />
        ))}
      </div>
    );
  }

  return (
    <div
      className="grid"
      style={{ '--cols': wordLength }}
      role="grid"
      aria-label="Wordle board"
    >
      {rows}
    </div>
  );
}
