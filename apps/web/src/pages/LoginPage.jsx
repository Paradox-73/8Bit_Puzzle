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

  // We only accept @gmail.com addresses, so the user types just the handle and we append the domain.
  const [emailLocal, setEmailLocal] = useState('');
  // TRIAL: the 2026 juniors haven't been assigned roll numbers yet, so they pick their batch
  // instead. The chosen batch rides through the existing `rollNumber` field to the backend.
  // Original roll-number state (restore when roll numbers are issued):
  // const [rollNumber, setRollNumber] = useState('');
  const [batch, setBatch] = useState('');
  const [username, setUsername] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState('details'); // 'details' | 'code'
  const [busy, setBusy] = useState(false);

  const email = emailLocal.trim().toLowerCase() + '@gmail.com';

  // Step 1: create-or-login + email a code.
  const sendCode = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await start({
        email,
        // rollNumber: rollNumber.trim(),  // original — restore when roll numbers return
        rollNumber: batch, // trial: the batch/programme carries the identity field for now
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
      await verifyCode(email, code.trim());
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
              <div className="input-affix">
                <input
                  className="input input--affixed"
                  type="text"
                  value={emailLocal}
                  // Strip anything from '@' on — the domain is fixed to gmail.com.
                  onChange={(e) => setEmailLocal(e.target.value.replace(/@.*/, '').trim())}
                  autoComplete="username"
                  inputMode="email"
                  placeholder="yourname"
                  required
                />
                <span className="input-affix__suffix">@gmail.com</span>
              </div>
            </label>
            {/* TRIAL: roll numbers aren't issued to the 2026 juniors yet, so collect their
                batch instead. The original roll-number input is preserved (commented) below. */}
            <label className="field">
              <span className="field__label">Batch</span>
              <select
                className="input"
                value={batch}
                onChange={(e) => setBatch(e.target.value)}
                required
              >
                <option value="" disabled>
                  Select your batch
                </option>
                <option value="iMTech CSE">iMTech CSE</option>
                <option value="iMTech ECE">iMTech ECE</option>
                <option value="BTech CSE">BTech CSE</option>
                <option value="BTech ECE">BTech ECE</option>
                <option value="BTech AI & DS">BTech AI &amp; DS</option>
              </select>
            </label>
            {/* Original roll-number field — restore when roll numbers are assigned:
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
            */}
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
