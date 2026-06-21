// ============================================================
// 8Bit Daily Puzzle — theme (light/dark) state + persistence.
// Dependency-free. The initial theme is applied by the inline
// boot script in index.html BEFORE React renders (no flash);
// this module keeps React in sync and handles toggling.
// ============================================================

import { useCallback, useEffect, useState } from 'react';

export const THEME_KEY = '8bit.theme';

// Theme-color values for the <meta name="theme-color"> tag, matched to --bg.
const META_COLOR = { dark: '#0f0f14', light: '#f4f6fb' };

/** Read the persisted choice, falling back to the OS preference. */
export function getInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    /* localStorage may be unavailable (private mode); ignore */
  }
  const prefersDark =
    typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}

/** Apply a theme to <html> and keep the address-bar colour in sync. */
export function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', META_COLOR[theme] || META_COLOR.dark);
}

/**
 * useTheme — returns [theme, toggle, setTheme].
 * A manual choice persists in localStorage and wins over the OS preference.
 */
export function useTheme() {
  const [theme, setThemeState] = useState(() => {
    // Trust whatever the boot script already set on <html>, else compute it.
    const current = document.documentElement.dataset.theme;
    return current === 'light' || current === 'dark' ? current : getInitialTheme();
  });

  const setTheme = useCallback((next) => {
    setThemeState(next);
    applyTheme(next);
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      /* ignore persistence failures */
    }
  }, []);

  const toggle = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  }, [theme, setTheme]);

  // Follow OS changes only while the user hasn't made a manual choice.
  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e) => {
      let saved = null;
      try {
        saved = localStorage.getItem(THEME_KEY);
      } catch {
        /* ignore */
      }
      if (saved !== 'light' && saved !== 'dark') {
        const next = e.matches ? 'dark' : 'light';
        setThemeState(next);
        applyTheme(next);
      }
    };
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  return [theme, toggle, setTheme];
}
