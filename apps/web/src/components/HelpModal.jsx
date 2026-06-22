import { useState } from 'react';
import FeedbackModal from './FeedbackModal.jsx';

// Small coloured example tile reusing the board tile styles.
function ExampleTile({ letter, state }) {
  return (
    <div className={'tile tile--filled tile--' + state} style={{ width: 38, height: 38 }}>
      <span className="tile__letter">{letter}</span>
    </div>
  );
}

function WordleHowTo() {
  return (
    <div className="help-section">
      <p>Guess the hidden <strong>5-letter word</strong> in 6 tries. Each guess must be a real word.</p>
      <p>After each guess the tiles change colour to show how close you were:</p>
      <div className="grid__row" style={{ margin: '10px 0' }}>
        <ExampleTile letter="P" state="green" />
        <ExampleTile letter="I" state="grey" />
        <ExampleTile letter="X" state="yellow" />
        <ExampleTile letter="E" state="grey" />
        <ExampleTile letter="L" state="grey" />
      </div>
      <ul className="help-list">
        <li><span className="swatch swatch--green" /> <strong>Blue</strong> — right letter, right spot.</li>
        <li><span className="swatch swatch--yellow" /> <strong>Yellow</strong> — in the word, wrong spot.</li>
        <li><span className="swatch swatch--grey" /> <strong>Grey</strong> — not in the word at all.</li>
      </ul>
      <p className="help-note">
        Stuck? Use a <strong>hint</strong> to learn one vowel and one consonant the word contains.
      </p>
    </div>
  );
}

const CONN_LEVELS = [
  ['l0', 'Yellow', 'Easiest — a simple, clear theme.'],
  ['l1', 'Green', 'Easy-ish — still a fairly direct link.'],
  ['l2', 'Blue', 'Trickier — trivia, abstract or cultural links.'],
  ['l3', 'Purple', 'Hardest — wordplay, hidden patterns, letter tricks.'],
];

function ConnectionsHowTo() {
  return (
    <div className="help-section">
      <p>Find the <strong>four groups of four</strong>. Tap four words you think share a connection, then Submit.</p>
      <p>You get <strong>four mistakes</strong>. Each group is colour-coded by difficulty — but the colour is only revealed once you’ve found the group:</p>
      <ul className="help-list">
        {CONN_LEVELS.map(([lvl, name, desc]) => (
          <li key={lvl}>
            <span className={'swatch swatch--' + lvl} /> <strong>{name}</strong> — {desc}
          </li>
        ))}
      </ul>
      <p className="help-note">
        “One away” means 3 of your 4 belong together. Stuck? The <strong>Hint</strong> button reveals
        one word from each group with its colour.
      </p>
    </div>
  );
}

function ConnectionsTips() {
  return (
    <ul className="help-list">
      <li>Watch for words that seem to fit <strong>two</strong> groups — that overlap is the trap.</li>
      <li>Solve the group you’re <strong>surest</strong> of first to clear the noise.</li>
      <li><strong>Purple</strong> is usually wordplay — hidden words, “___ + word”, homophones.</li>
      <li>Use <strong>Shuffle</strong> to break up patterns and spot new pairings.</li>
    </ul>
  );
}

function WordleTips() {
  return (
    <ul className="help-list">
      <li>Open with a word rich in vowels and common letters — <strong>AROSE</strong>, <strong>RAISE</strong>, <strong>SLATE</strong>.</li>
      <li>Use grey letters to rule options out; don’t reuse a letter you know is absent.</li>
      <li>Watch for <strong>repeated letters</strong> — a yellow can appear twice.</li>
      <li>Spend a middle guess testing new letters even if you have a few greens.</li>
    </ul>
  );
}

function CrypticHowTo() {
  return (
    <div className="help-section">
      <p>
        A cryptic clue has <strong>two halves</strong>: a straight <strong>definition</strong> (at the
        very start or very end) and some <strong>wordplay</strong> that spells the same answer another
        way. The number in brackets is the answer’s length.
      </p>
      <div className="help-example">
        <p className="help-example__clue">“Pay attention to broken tinsel (6)” → <strong>LISTEN</strong></p>
        <ul className="help-list">
          <li><strong>Definition:</strong> “Pay attention”</li>
          <li><strong>Indicator:</strong> “broken” → rearrange the letters</li>
          <li><strong>Fodder:</strong> “tinsel” → anagram of TINSEL = LISTEN</li>
        </ul>
      </div>
      <p className="help-note">
        Stuck? Reveal the <strong>definition</strong>, the <strong>indicator</strong>, or the
        <strong> fodder</strong> one at a time to nudge yourself toward the answer.
      </p>
    </div>
  );
}

const DEVICES = [
  ['Definition / synonym', 'Part of the clue is just a synonym of the answer.'],
  ['Anagram', 'An indicator (broken, mixed, confused, wild…) tells you to rearrange nearby letters.'],
  ['Hidden', 'The answer sits hidden inside consecutive words — flagged by “some of”, “in part”, “within”.'],
  ['Reversal', '“Back”, “returning”, “sent up” → read the letters backwards.'],
  ['Charade (selectors)', 'Build the answer from smaller pieces placed one after another, e.g. CAR + PET.'],
  ['Container', 'One piece goes inside another — “in”, “around”, “framed by”, “holds”.'],
  ['Deletion', 'Drop a letter — “headless”, “endless”, “loses its head/tail”.'],
  ['Homophone', '“We hear”, “reportedly”, “on the radio” → it sounds like another word.'],
  ['Symbols / abbreviations', 'Single letters stand for things: N=north, ST=street, R=king, L=left, O=love/zero…'],
];

function CrypticTips() {
  return (
    <div className="help-section">
      <ul className="help-list">
        <li><strong>Find the definition first</strong> — it’s almost always the first or last word.</li>
        <li>Spot the <strong>indicator</strong> word to learn which device is in play.</li>
        <li>Count letters against the <strong>(enumeration)</strong> in brackets.</li>
      </ul>
      <h3 className="help-subtitle">The devices</h3>
      <dl className="help-devices">
        {DEVICES.map(([name, desc]) => (
          <div key={name} className="help-device">
            <dt>{name}</dt>
            <dd>{desc}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function Faq() {
  return (
    <ul className="help-list">
      <li><strong>When’s the new puzzle?</strong> Every day at midnight IST. One attempt per day.</li>
      <li><strong>Streaks:</strong> solve each day to keep your 🔥 streak alive.</li>
      <li><strong>Do hints cost points?</strong> No — hints are free, use them to learn.</li>
      <li><strong>Please play fair</strong> — solve the puzzles yourself so the leaderboards stay
        meaningful and fun for everyone. 🙂</li>
      <li><strong>Found a bug or have an idea?</strong> Use the feedback button below.</li>
    </ul>
  );
}

const TITLES = { wordle: 'Wordle', connections: 'Connections', cryptic: 'Minute Cryptic' };
const HOWTO = { wordle: WordleHowTo, connections: ConnectionsHowTo, cryptic: CrypticHowTo };
const TIPS = { wordle: WordleTips, connections: ConnectionsTips, cryptic: CrypticTips };

export default function HelpModal({ game = 'wordle', initialTab = 'how', onClose }) {
  const [tab, setTab] = useState(initialTab);
  const [feedback, setFeedback] = useState(false);

  const tabs = [
    { id: 'how', label: 'How to play' },
    { id: 'tips', label: 'Tips & tricks' },
    { id: 'faq', label: 'FAQ' },
  ];

  const HowTo = HOWTO[game] || WordleHowTo;
  const Tips = TIPS[game] || WordleTips;

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div
          className="modal modal--help"
          onClick={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
        >
          <button className="modal__close" onClick={onClose} aria-label="Close">
            ✕
          </button>
          <h2 className="result-title">{TITLES[game] || 'How to play'}</h2>

          <div className="segmented help-tabs" role="tablist">
            {tabs.map((t) => (
              <button
                key={t.id}
                role="tab"
                aria-selected={tab === t.id}
                className={'seg' + (tab === t.id ? ' seg--active' : '')}
                onClick={() => setTab(t.id)}
              >
                {t.label}
              </button>
            ))}
          </div>

          <div className="help-body">
            {tab === 'how' && <HowTo />}
            {tab === 'tips' && <Tips />}
            {tab === 'faq' && <Faq />}
          </div>

          <button className="btn btn--ghost btn--block" onClick={() => setFeedback(true)}>
            💬 Feedback / report a bug
          </button>
        </div>
      </div>
      {feedback && <FeedbackModal onClose={() => setFeedback(false)} />}
    </>
  );
}
