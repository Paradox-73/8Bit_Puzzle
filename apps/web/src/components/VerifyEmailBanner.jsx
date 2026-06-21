// Pixel-styled "Verify your email" banner.
// Shows only when a logged-in user has emailVerified === false.
// Lets the user enter a 6-digit OTP, resend a code, and dismiss (the dismissal
// is per-session so it stays out of the way but reappears next visit until
// the email is actually verified). On success it refreshes /me via auth.

import { useState } from 'react';
import { api, ApiError } from '../api.js';
import { useAuth } from '../auth.jsx';

// Map backend OTP error codes to human, arcade-friendly copy.
const ERROR_COPY = {
  BAD_OTP: 'Wrong code — try again.',
  OTP_EXPIRED: 'That code expired. Tap RESEND for a new one.',
  OTP_NOT_PENDING: 'No code pending. Tap RESEND to get one.',
  RATE_LIMITED: 'Too many tries — wait a moment before resending.',
};

export default function VerifyEmailBanner() {
  const { user, refreshUser } = useAuth();
  const [dismissed, setDismissed] = useState(false);
  const [code, setCode] = useState('');
  // status: idle | verifying | sending | sent | error | success
  const [status, setStatus] = useState('idle');
  const [message, setMessage] = useState('');

  // Nothing to show: verified (default in dev), no user, or dismissed this session.
  if (!user || user.emailVerified !== false || dismissed) return null;

  const onCodeChange = (e) => {
    // digits only, max 6
    const next = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(next);
    if (status === 'error' || status === 'sent') {
      setStatus('idle');
      setMessage('');
    }
  };

  const verify = async (e) => {
    e.preventDefault();
    if (code.length !== 6 || status === 'verifying') return;
    setStatus('verifying');
    setMessage('');
    try {
      await api.verifyOtp(code);
      setStatus('success');
      setMessage('Verified! Thanks.');
      // Refresh /me so emailVerified flips true and this banner unmounts.
      await refreshUser();
    } catch (err) {
      const codeErr = err instanceof ApiError ? err.code : null;
      setStatus('error');
      setMessage(ERROR_COPY[codeErr] || 'Could not verify. Try again.');
    }
  };

  const resend = async () => {
    if (status === 'sending') return;
    setStatus('sending');
    setMessage('');
    try {
      await api.resendOtp();
      setStatus('sent');
      setMessage('New code sent — check your inbox.');
    } catch (err) {
      const codeErr = err instanceof ApiError ? err.code : null;
      setStatus('error');
      setMessage(ERROR_COPY[codeErr] || 'Could not send a code right now.');
    }
  };

  const busy = status === 'verifying' || status === 'sending';

  return (
    <section
      className="verify-banner"
      role="region"
      aria-label="Verify your email"
    >
      <button
        type="button"
        className="verify-banner__close"
        onClick={() => setDismissed(true)}
        aria-label="Dismiss for now"
      >
        ✕
      </button>

      <div className="verify-banner__head">
        <span className="verify-banner__icon" aria-hidden="true">
          ✉
        </span>
        <span className="verify-banner__title">VERIFY YOUR EMAIL</span>
      </div>

      <p className="verify-banner__text">
        Enter the 6-digit code we emailed you to verify your account.
      </p>

      <form className="verify-banner__form" onSubmit={verify}>
        <label className="sr-only" htmlFor="otp-code">
          6-digit verification code
        </label>
        <input
          id="otp-code"
          className="input input--mono verify-banner__input"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]*"
          placeholder="------"
          maxLength={6}
          value={code}
          onChange={onCodeChange}
          aria-describedby="otp-msg"
          disabled={busy}
        />
        <button
          type="submit"
          className="btn btn--primary"
          disabled={code.length !== 6 || busy}
        >
          {status === 'verifying' ? 'CHECKING…' : 'VERIFY'}
        </button>
      </form>

      <div className="verify-banner__foot">
        <button
          type="button"
          className="btn btn--ghost btn--small"
          onClick={resend}
          disabled={busy}
        >
          {status === 'sending' ? 'SENDING…' : 'RESEND CODE'}
        </button>
        {message && (
          <span
            id="otp-msg"
            className={
              'verify-banner__msg' +
              (status === 'error'
                ? ' verify-banner__msg--error'
                : status === 'success' || status === 'sent'
                ? ' verify-banner__msg--ok'
                : '')
            }
            role="status"
            aria-live="polite"
          >
            {message}
          </span>
        )}
      </div>
    </section>
  );
}
