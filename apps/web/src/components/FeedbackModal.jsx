import { useState } from 'react';
import { api, ApiError } from '../api.js';
import { useToast } from './Toast.jsx';

// Feedback / bug report form. Posts to /feedback which persists + emails the team.
export default function FeedbackModal({ initialType = 'feedback', onClose }) {
  const { toast } = useToast();
  const [type, setType] = useState(initialType);
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!message.trim()) {
      toast('Please tell us a bit more', { type: 'warn' });
      return;
    }
    setSending(true);
    try {
      await api.feedback({
        type,
        message: message.trim(),
        context: window.location.pathname,
      });
      toast('Thanks! Sent to the 8Bit team.', { type: 'success' });
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'RATE_LIMITED') {
        toast(err.message, { type: 'warn' });
      } else {
        toast(err.message || 'Could not send — try again', { type: 'error' });
      }
    } finally {
      setSending(false);
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
        <h2 className="section-title">Send feedback</h2>

        <form onSubmit={submit} className="feedback-form">
          <div className="segmented" role="tablist" aria-label="Type">
            <button
              type="button"
              role="tab"
              aria-selected={type === 'feedback'}
              className={'seg' + (type === 'feedback' ? ' seg--active' : '')}
              onClick={() => setType('feedback')}
            >
              💡 Feedback
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={type === 'bug'}
              className={'seg' + (type === 'bug' ? ' seg--active' : '')}
              onClick={() => setType('bug')}
            >
              🐞 Report a bug
            </button>
          </div>

          <textarea
            className="feedback-input"
            rows={5}
            maxLength={2000}
            placeholder={
              type === 'bug'
                ? 'What went wrong? What were you doing when it happened?'
                : 'What do you love? What could be better?'
            }
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            autoFocus
          />

          <button
            className="btn btn--primary btn--block btn--lg"
            type="submit"
            disabled={sending}
          >
            {sending ? 'Sending…' : 'Send'}
          </button>
        </form>
      </div>
    </div>
  );
}
