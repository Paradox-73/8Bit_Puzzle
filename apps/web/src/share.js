// Appends a short call-to-action + play link to a result's emoji grid for the
// "Share result" button. Kept deliberately plain (no hype/marketing voice) —
// the emoji grid stays the hero, the footer is two terse lines.
export function withShareFooter(grid) {
  const link = typeof window !== 'undefined' ? window.location.origin : '';
  return (
    `${grid}\n\n` +
    `Your move → ${link}\n` +
    `Daily 8Bit puzzle — top the campus board, carry your batch in the Batch Wars.`
  );
}
