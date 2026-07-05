import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import ThemeToggle from '../components/ThemeToggle.jsx';

// One screen handles both sign-up and login: enter your details, then the emailed code.
// New email -> account created; existing email -> logged in (roll + username must match).
export default function LoginPage() {
  const { start, verifyCode } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from || '/';

  const [email, setEmail] = useState('');
  const [rollNumber, setRollNumber] = useState('');
  const [username, setUsername] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState('details'); // 'details' | 'code'
  const [busy, setBusy] = useState(false);

  // Step 1: create-or-login + email a code.
  const sendCode = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await start({
        email: email.trim().toLowerCase(),
        rollNumber: rollNumber.trim(),
        username: username.trim(),
      });
      setStep('code');
      toast('We emailed you a 6-digit code.', { type: 'success' });
    } catch (err) {
      toast(err.message || 'Could not continue', { type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  // Step 2: verify the code.
  const submitCode = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await verifyCode(email.trim().toLowerCase(), code.trim());
      navigate(from, { replace: true });
    } catch (err) {
      toast(err.message || 'Invalid code', { type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <ThemeToggle className="theme-toggle--corner" />
      <div className="auth-card">
        <h1 className="logo">
          <span className="logo__bit">8</span>BIT
        </h1>
        <p className="auth-sub">Daily Puzzle</p>

        {step === 'details' ? (
          <form onSubmit={sendCode} className="form">
            <p className="auth-sub auth-sub--muted">
              New here or returning — enter your details to continue.
            </p>
            <label className="field">
              <span className="field__label">Email</span>
              <input
                className="input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                placeholder="you@gmail.com"
                required
              />
            </label>
            <label className="field">
              <span className="field__label">Roll Number</span>
              <input
                className="input"
                value={rollNumber}
                onChange={(e) => setRollNumber(e.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label className="field">
              <span className="field__label">Username</span>
              <input
                className="input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="nickname"
                required
              />
            </label>
            <button className="btn btn--primary btn--block" disabled={busy}>
              {busy ? 'Please wait…' : 'Continue'}
            </button>
          </form>
        ) : (
          <form onSubmit={submitCode} className="form">
            <p className="auth-sub">Enter the code sent to {email}</p>
            <label className="field">
              <span className="field__label">6-digit code</span>
              <input
                className="input"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                required
              />
            </label>
            <button className="btn btn--primary btn--block" disabled={busy}>
              {busy ? 'Verifying…' : 'Continue'}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--block"
              onClick={() => { setStep('details'); setCode(''); }}
              disabled={busy}
            >
              ← Edit details
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
