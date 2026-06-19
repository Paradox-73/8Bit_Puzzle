import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const items = [
  { to: '/', label: 'Home', icon: '⌂', end: true },
  { to: '/play', label: 'Play', icon: '▶' },
  { to: '/leaderboard', label: 'Ranks', icon: '★' },
  { to: '/profile', label: 'Me', icon: '☻' },
];

export default function NavBar() {
  const { isEditor } = useAuth();

  return (
    <nav className="navbar" aria-label="Primary">
      {items.map((it) => (
        <NavLink
          key={it.to}
          to={it.to}
          end={it.end}
          className={({ isActive }) =>
            'navbar__item' + (isActive ? ' navbar__item--active' : '')
          }
        >
          <span className="navbar__icon" aria-hidden="true">
            {it.icon}
          </span>
          <span className="navbar__label">{it.label}</span>
        </NavLink>
      ))}
      {isEditor && (
        <NavLink
          to="/admin"
          className={({ isActive }) =>
            'navbar__item' + (isActive ? ' navbar__item--active' : '')
          }
        >
          <span className="navbar__icon" aria-hidden="true">
            ⚙
          </span>
          <span className="navbar__label">Admin</span>
        </NavLink>
      )}
    </nav>
  );
}
