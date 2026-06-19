// On-screen keyboard. Colours each key by the best-known state for that letter.

const ROWS = [
  ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
  ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
  ['ENTER', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', 'BACK'],
];

export default function Keyboard({ keyStates = {}, onKey, disabled = false }) {
  const press = (k) => {
    if (disabled) return;
    onKey(k);
  };

  return (
    <div className="keyboard" aria-label="On-screen keyboard">
      {ROWS.map((row, i) => (
        <div className="keyboard__row" key={i}>
          {row.map((k) => {
            const isAction = k === 'ENTER' || k === 'BACK';
            const state = !isAction ? keyStates[k] : null;
            const cls =
              'key' +
              (isAction ? ' key--wide' : '') +
              (state ? ` key--${state.toLowerCase()}` : '');
            return (
              <button
                type="button"
                key={k}
                className={cls}
                onClick={() => press(k)}
                disabled={disabled}
                aria-label={k === 'BACK' ? 'Backspace' : k}
              >
                {k === 'BACK' ? '⌫' : k}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}

// Compute best key state across all guesses. GREEN > YELLOW > GREY.
export function computeKeyStates(guesses = []) {
  const rank = { GREY: 1, YELLOW: 2, GREEN: 3 };
  const out = {};
  for (const g of guesses) {
    if (!g.guess || !g.result) continue;
    const letters = g.guess.toUpperCase().split('');
    for (let i = 0; i < letters.length; i++) {
      const L = letters[i];
      const s = g.result[i];
      if (!s) continue;
      if (!out[L] || rank[s] > rank[out[L]]) out[L] = s;
    }
  }
  return out;
}
