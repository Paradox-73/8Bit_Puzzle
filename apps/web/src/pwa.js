import { useEffect, useState } from 'react';

// There is no reliable cross-browser "is this PWA installed?" API. We combine:
//  - display-mode: standalone / navigator.standalone  -> currently running as the installed app
//  - a persisted flag set from the `appinstalled` event -> remembered even in a normal tab later
//  - the `beforeinstallprompt` event (Chromium only)    -> installable AND not yet installed
// iOS Safari fires none of these, so it only ever gets manual "Add to Home Screen" instructions.

const INSTALLED_KEY = '8bit.installed';

export function isIos() {
  return /iphone|ipad|ipod/i.test(window.navigator.userAgent) && !window.MSStream;
}

export function isStandalone() {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    window.navigator.standalone === true
  );
}

// `beforeinstallprompt` fires once, early — often before a given page mounts. Capture it at module
// load into a shared store so any component (e.g. the Profile page) can use it whenever it renders.
let deferredPrompt = null;
const subscribers = new Set();
let started = false;

function notify() {
  subscribers.forEach((fn) => fn());
}

function start() {
  if (started || typeof window === 'undefined') return;
  started = true;

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault(); // stop the mini-infobar; we trigger it from our own UI
    deferredPrompt = e;
    notify();
  });
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    try {
      localStorage.setItem(INSTALLED_KEY, '1');
    } catch {
      /* storage may be unavailable in private mode */
    }
    notify();
  });

  if (isStandalone()) {
    try {
      localStorage.setItem(INSTALLED_KEY, '1');
    } catch {
      /* ignore */
    }
  }
}
start();

/**
 * @returns {{ installed: boolean, canPrompt: boolean, isIos: boolean, promptInstall: () => Promise<void> }}
 *  installed   – best-effort: running standalone or previously installed
 *  canPrompt   – a native install prompt is available (Chromium, not yet installed)
 *  isIos       – needs the manual Share → Add to Home Screen flow
 *  promptInstall – fires the native install dialog
 */
export function useInstallPrompt() {
  const [, force] = useState(0);

  useEffect(() => {
    const fn = () => force((n) => n + 1);
    subscribers.add(fn);
    return () => subscribers.delete(fn);
  }, []);

  let persisted = false;
  try {
    persisted = localStorage.getItem(INSTALLED_KEY) === '1';
  } catch {
    /* ignore */
  }
  const installed = isStandalone() || persisted;

  const promptInstall = async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    try {
      await deferredPrompt.userChoice;
    } finally {
      deferredPrompt = null;
      notify();
    }
  };

  return { installed, canPrompt: !!deferredPrompt, isIos: isIos(), promptInstall };
}
