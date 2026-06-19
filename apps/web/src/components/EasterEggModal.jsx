// Celebratory modal shown when a guess/move response includes an easterEgg.
// Reuses the shared .modal styling. Works for both Wordle and Connections.

export default function EasterEggModal({ egg, onClose }) {
  if (!egg) return null;
  const { title, body } = egg;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal easter-modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button className="modal__close" onClick={onClose} aria-label="Close">
          ✕
        </button>

        <div className="easter-modal__egg" aria-hidden="true">
          🥚
        </div>
        <h2 className="easter-modal__heading">Easter egg!</h2>
        {title && <h3 className="easter-modal__title">{title}</h3>}
        {body && <p className="easter-modal__body">{body}</p>}

        <button className="btn btn--primary btn--block" onClick={onClose}>
          Nice!
        </button>
      </div>
    </div>
  );
}
