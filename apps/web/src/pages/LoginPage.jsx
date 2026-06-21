import { useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import ThemeToggle from '../components/ThemeToggle.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from || '/';

  const [rollNumber, setRollNumber] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await login({ rollNumber: rollNumber.trim(), password });
      navigate(from, { replace: true });
    } catch (err) {
      toast(err.message || 'Login failed', { type: 'error' });
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

        <form onSubmit={submit} className="form">
          <label className="field">
            <span className="field__label">Roll Number</span>
            <input
              className="input"
              value={rollNumber}
              onChange={(e) => setRollNumber(e.target.value)}
              autoComplete="username"
              inputMode="text"
              required
            />
          </label>
          <label className="field">
            <span className="field__label">Password</span>
            <input
              className="input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          <button className="btn btn--primary btn--block" disabled={busy}>
            {busy ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p className="auth-foot">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
