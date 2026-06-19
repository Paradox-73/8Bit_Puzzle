import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';

export default function RegisterPage() {
  const { register } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [rollNumber, setRollNumber] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await register({
        rollNumber: rollNumber.trim(),
        username: username.trim(),
        password,
      });
      navigate('/', { replace: true });
    } catch (err) {
      toast(err.message || 'Registration failed', { type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="logo">
          <span className="logo__bit">8</span>BIT
        </h1>
        <p className="auth-sub">Join the daily grind</p>

        <form onSubmit={submit} className="form">
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
          <label className="field">
            <span className="field__label">Password</span>
            <input
              className="input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password"
              required
            />
          </label>
          <button className="btn btn--primary btn--block" disabled={busy}>
            {busy ? 'Creating…' : 'Create account'}
          </button>
        </form>

        <p className="auth-foot">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </div>
    </div>
  );
}
