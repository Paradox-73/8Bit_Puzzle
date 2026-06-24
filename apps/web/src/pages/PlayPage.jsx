import { useCallback, useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api, ApiError } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import WordleGame from './WordleGame.jsx';
import ConnectionsGame from './ConnectionsGame.jsx';
import CrypticGame from './CrypticGame.jsx';
import HelpModal from '../components/HelpModal.jsx';
import TrialFeedback from '../components/TrialFeedback.jsx';

// Supported games. Default is wordle. The selected game lives in the
// ?game= query param so it is bookmarkable, e.g. /play?game=connections.
const GAMES = [
  { type: 'wordle', label: 'Wordle' },
  { type: 'connections', label: 'Connections' },
  { type: 'cryptic', label: 'Cryptic' },
];

const TITLES = {
  wordle: 'Wordle',
  connections: 'Connections',
  cryptic: 'Minute Cryptic',
};

// Which games have a How-to-Play / Tips / FAQ guide.
const HELP_GAMES = new Set(['wordle', 'connections', 'cryptic']);

export default function PlayPage() {
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const raw = (searchParams.get('game') || 'wordle').toLowerCase();
  const game = GAMES.some((g) => g.type === raw) ? raw : 'wordle';

  const [puzzle, setPuzzle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [noPuzzle, setNoPuzzle] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  // DOM node in the header that Wordle portals its 💡 hint control into, so the
  // hint lives next to the "?" instead of taking a row below the grid.
  const [headerSlot, setHeaderSlot] = useState(null);
  const hasHelp = HELP_GAMES.has(game);

  const load = useCallback(async () => {
    setLoading(true);
    setNoPuzzle(false);
    setPuzzle(null);
    try {
      const data = await api.getToday(game);
      setPuzzle(data);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'NO_PUZZLE' || err.status === 404) {
          setNoPuzzle(true);
        } else if (err.code === 'RATE_LIMITED') {
          toast(err.message, { type: 'warn' });
        } else {
          toast(err.message || 'Could not load today’s puzzle', { type: 'error' });
        }
      } else {
        toast('Could not load today’s puzzle', { type: 'error' });
      }
    } finally {
      setLoading(false);
    }
  }, [game, toast]);

  useEffect(() => {
    load();
  }, [load]);

  // First-timer onboarding: auto-open the guide once per game per browser (NYT-style).
  useEffect(() => {
    if (loading || noPuzzle || !puzzle || !hasHelp) return;
    const key = '8bit.howto.' + game;
    if (!localStorage.getItem(key)) {
      setShowHelp(true);
      localStorage.setItem(key, '1');
    }
  }, [loading, noPuzzle, puzzle, game, hasHelp]);

  const selectGame = (type) => {
    setSearchParams(type === 'wordle' ? {} : { game: type });
  };

  return (
    <div className="page page--play">
      <div className="segmented play-switcher" role="tablist" aria-label="Choose game">
        {GAMES.map((g) => (
          <button
            key={g.type}
            role="tab"
            aria-selected={g.type === game}
            className={'seg' + (g.type === game ? ' seg--active' : '')}
            onClick={() => selectGame(g.type)}
          >
            {g.label}
          </button>
        ))}
      </div>

      <header className="play-header">
        <h2 className="play-title">{TITLES[game]}</h2>
        <div className="play-header__right">
          {puzzle?.date && <span className="play-sub">{puzzle.date}</span>}
          <span className="hint-slot" ref={setHeaderSlot} />
          {hasHelp && (
            <button
              className="help-btn"
              onClick={() => setShowHelp(true)}
              aria-label="How to play"
              title="How to play"
            >
              ?
            </button>
          )}
        </div>
      </header>

      {/* Heads-up when today's answer isn't a normal dictionary word (an IIITB term / brand / slang).
          Wordle & Cryptic only — Connections tiles carry their own context in the category names. */}
      {puzzle?.campusWord && (game === 'wordle' || game === 'cryptic') && (
        <p className="campus-badge" title="Today's answer may not be in the dictionary">
          🏫 Campus word — might not be in the dictionary
        </p>
      )}

      {/* Pre-launch playtest: walk every puzzle back-to-back, no daily limit. Server-driven —
          this only renders while trial mode is on, and disappears entirely once it's off. */}
      {puzzle?.trial && !puzzle.trialDone && (
        <div className="trial-banner">
          <span className="trial-banner__tag">🧪 TRIAL</span>
          <span className="trial-banner__progress">
            Puzzle {puzzle.trialIndex} of {puzzle.trialTotal} · no daily limit
          </span>
          <button className="btn btn--small" onClick={load}>
            Next puzzle →
          </button>
        </div>
      )}

      {loading && <div className="loading">Loading puzzle…</div>}

      {!loading && puzzle?.trialDone && (
        <div className="empty">
          <p>
            {puzzle.trialTotal > 0
              ? `🎉 You've played all ${puzzle.trialTotal} ${TITLES[game]} puzzles. Thanks for testing!`
              : `No ${TITLES[game]} puzzles to test yet.`}
          </p>
        </div>
      )}

      {!loading && noPuzzle && (
        <div className="empty conn-empty">
          <p>
            {game === 'wordle'
              ? 'No Wordle puzzle today — check back tomorrow.'
              : 'No Connections puzzle today — play Wordle instead.'}
          </p>
          {game !== 'wordle' && (
            <Link className="btn btn--primary" to="/play">
              Play Wordle instead
            </Link>
          )}
        </div>
      )}

      {!loading && puzzle && !puzzle.trialDone && puzzle.gameType === 'connections' && (
        <ConnectionsGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
      )}

      {!loading && puzzle && !puzzle.trialDone && puzzle.gameType === 'cryptic' && (
        <CrypticGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} />
      )}

      {!loading && puzzle && !puzzle.trialDone &&
        puzzle.gameType !== 'connections' && puzzle.gameType !== 'cryptic' && (
          <WordleGame key={puzzle.puzzleId} puzzle={puzzle} reload={load} headerSlot={headerSlot} />
        )}

      {puzzle?.trial && !puzzle.trialDone && puzzle.puzzleId && (
        <TrialFeedback key={puzzle.puzzleId} puzzle={puzzle} />
      )}

      {showHelp && (
        <HelpModal game={game} onClose={() => setShowHelp(false)} />
      )}
    </div>
  );
}
