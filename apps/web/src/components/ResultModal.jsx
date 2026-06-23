import { Link } from 'react-router-dom';
import { useToast } from './Toast.jsx';
import { withShareFooter } from '../share.js';

export default function ResultModal({ result, streak, puzzle, onClose }) {
  const { toast } = useToast();
  const { solved, answer, shareGrid, parse } = result;

  // Build a fallback share text if the server didn't send shareGrid
  // (e.g. when result was restored from /today which has no shareGrid).
  const buildFallbackShare = () => {
    const used = (puzzle?.guesses?.length ?? 0) || '?';
    const max = puzzle?.config?.maxGuesses ?? 6;
    const grid = (puzzle?.guesses || [])
      .map((g) =>
        (g.result || [])
          .map((r) => (r === 'GREEN' ? '🟩' : r === 'YELLOW' ? '🟨' : '⬛'))
          .join('')
      )
      .join('\n');
    return `8Bit • Wordle • ${solved ? used : 'X'}/${max}\n${grid}`;
  };

  const share = async () => {
    const text = withShareFooter(shareGrid || buildFallbackShare());
    try {
      if (navigator.share) {
        await navigator.share({ text });
        return;
      }
    } catch {
      // user cancelled or share failed — fall through to clipboard
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

        {answer && (
          <p className="result-answer">
            Answer: <strong>{String(answer).toUpperCase()}</strong>
          </p>
        )}

        {streak != null && (
          <p className="result-streak">
            🔥 Streak: <strong>{streak}</strong>
          </p>
        )}

        {parse && (
          <div className="cryptic-parse">
            {parse.device && (
              <p className="cryptic-parse__device">Device: <strong>{parse.device}</strong></p>
            )}
            <dl className="cryptic-parse__grid">
              {parse.definition && (<><dt>Definition</dt><dd>{parse.definition}</dd></>)}
              {parse.indicator && (<><dt>Indicator</dt><dd>{parse.indicator}</dd></>)}
              {parse.fodder && (<><dt>Fodder</dt><dd>{parse.fodder}</dd></>)}
            </dl>
            {parse.explanation && <p className="cryptic-parse__how">{parse.explanation}</p>}
          </div>
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
