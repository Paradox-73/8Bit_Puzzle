import { useTheme } from '../theme.js';

/**
 * ThemeToggle — small pixel-styled sun/moon button.
 * Accessible: real <button>, aria-label, aria-pressed, keyboard focusable.
 */
export default function ThemeToggle({ className = '' }) {
  const [theme, toggle] = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      className={'theme-toggle' + (className ? ' ' + className : '')}
      onClick={toggle}
      aria-label="Toggle theme"
      aria-pressed={isDark}
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      <span className="theme-toggle__icon" aria-hidden="true">
        {isDark ? '☾' : '☀'}
      </span>
    </button>
  );
}
