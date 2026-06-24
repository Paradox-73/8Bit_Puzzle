// Trial-only: lets a playtester rate the current puzzle (1–5) and leave a "what to change" note.
// Server-driven (only rendered in trial mode); part of trial mode, removable with it.

import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useToast } from './Toast.jsx';

export default function TrialFeedback({ puzzle }) {
  const { toast } = useToast();
  const [rating, setRating] = useState(puzzle.myRating || 0);
  const [hover, setHover] = useState(0);
  const [message, setMessage] = useState(puzzle.myFeedback || '');
  const [saving, setSaving] = useState(false);

  // Reset when moving to a different puzzle.
  useEffect(() => {
    setRating(puzzle.myRating || 0);
    setMessage(puzzle.myFeedback || '');
  }, [puzzle.puzzleId, puzzle.myRating, puzzle.myFeedback]);

  const submit = async () => {
    if (!rating && !message.trim()) {
      toast('Pick a rating or write a note first', { type: 'warn' });
      return;
    }
    setSaving(true);
    try {
      await api.ratePuzzle(puzzle.puzzleId, rating || null, message.trim() || null);
      toast('Thanks — feedback saved!', { type: 'success' });
    } catch (err) {
      toast(err.message || 'Could not save feedback', { type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="trial-fb" aria-label="Rate this puzzle">
      <span className="trial-fb__label">Rate this puzzle</span>
      <div className="trial-fb__stars" role="radiogroup" aria-label="Star rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            className={'trial-fb__star' + ((hover || rating) >= n ? ' trial-fb__star--on' : '')}
            onMouseEnter={() => setHover(n)}
            onMouseLeave={() => setHover(0)}
            onClick={() => setRating(n === rating ? 0 : n)}
            aria-label={`${n} star${n > 1 ? 's' : ''}`}
            aria-pressed={rating === n}
          >
            ★
          </button>
        ))}
      </div>
      <textarea
        className="input trial-fb__msg"
        rows={2}
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        maxLength={1000}
        placeholder="Anything to change? Too easy/hard, unfair clue, typo…"
      />
      <button className="btn btn--small" onClick={submit} disabled={saving}>
        {saving ? 'Saving…' : 'Send feedback'}
      </button>
    </section>
  );
}
