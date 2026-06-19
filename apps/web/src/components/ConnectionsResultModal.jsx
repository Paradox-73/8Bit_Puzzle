import { Link } from 'react-router-dom';
import { useToast } from './Toast.jsx';

// Result modal for Connections. Reveals all solved groups and offers a one-tap
// share using the server's emoji shareGrid (falls back to a simple summary).
export default function ConnectionsResultModal({ result, streak, puzzle, onClose }) {
  const { toast } = useToast();
  const { solved, score, shareGrid, solvedGroups = [] } = result;

  const buildFallbackShare = () => {
    const max = puzzle?.config?.maxMistakes ?? 4;
    const made = solvedGroups.length;
    return `8Bit • Connections • ${solved ? `${made}/4` : 'X'} (≤${max} mistakes)`;
  };

  const share = async () => {
    const text = shareGrid || buildFallbackShare();
    try {
      if (navigator.share) {
        await navigator.share({ text });
        return;
      }
    } catch {
      // cancelled / unsupported — fall through to clipboard
    }
    try {
      await navigator.clipboard.writeText(text);
      toast('Copied result to clipboard!', { type: 'success' });
    } catch {
      toast('Could not copy. Long-press to copy manually.', { type: 'error' });
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button className="modal__close" onClick={onClose} aria-label="Close">
          ✕
        </button>

        <h2 className={'result-title ' + (solved ? 'result-title--win' : 'result-title--lose')}>
          {solved ? '✓ Solved!' : '✗ Out of guesses'}
        </h2>

        {score != null && <div className="result-score">{score} pts</div>}

        {solvedGroups.length > 0 && (
          <div className="conn-solved-stack conn-solved-stack--result">
            {solvedGroups
              .slice()
              .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
              .map((g) => (
                <div
                  key={g.level ?? g.category}
                  className={`conn-solved conn-solved--l${g.level ?? 0}`}
                >
                  <div className="conn-solved__cat">{g.category}</div>
                  <div className="conn-solved__members">
                    {(g.members || []).join(' · ')}
                  </div>
                </div>
              ))}
          </div>
        )}

        {streak != null && (
          <p className="result-streak">
            🔥 Streak: <strong>{streak}</strong>
          </p>
        )}

        <button className="btn btn--primary btn--block btn--lg" onClick={share}>
          📋 Share result
        </button>

        <div className="result-actions">
          <Link className="btn btn--ghost" to="/leaderboard">
            Leaderboard
          </Link>
          <Link className="btn btn--ghost" to="/">
            Home
          </Link>
        </div>
      </div>
    </div>
  );
}
