import { useEffect, useState } from 'react';

const DISMISS_KEY = '8bit.installHintDismissed';

function isIos() {
  return (
    /iphone|ipad|ipod/i.test(window.navigator.userAgent) &&
    !window.MSStream
  );
}

function isStandalone() {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    window.navigator.standalone === true
  );
}

// Shows an "Add to Home Screen" / install hint.
// - Android/desktop: uses the captured beforeinstallprompt event.
// - iOS Safari: shows manual instructions (no programmatic prompt available).
export default function InstallHint() {
  const [deferred, setDeferred] = useState(null);
  const [show, setShow] = useState(false);
  const [iosHint, setIosHint] = useState(false);

  useEffect(() => {
    if (localStorage.getItem(DISMISS_KEY)) return;
    if (isStandalone()) return;

    const onBeforeInstall = (e) => {
      e.preventDefault();
      setDeferred(e);
      setShow(true);
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);

    // iOS never fires beforeinstallprompt — show manual hint instead.
    if (isIos()) {
      setIosHint(true);
      setShow(true);
    }

    return () => window.removeEventListener('beforeinstallprompt', onBeforeInstall);
  }, []);

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, '1');
    setShow(false);
  };

  const install = async () => {
    if (!deferred) return;
    deferred.prompt();
    try {
      await deferred.userChoice;
    } finally {
      setDeferred(null);
      dismiss();
    }
  };

  if (!show) return null;

  return (
    <div className="install-hint" role="dialog" aria-label="Install app">
      <button className="install-hint__close" onClick={dismiss} aria-label="Dismiss">
        ✕
      </button>
      {iosHint ? (
        <p className="install-hint__text">
          Install 8Bit: tap <strong>Share</strong> <span aria-hidden="true">⎙</span> then{' '}
          <strong>Add to Home Screen</strong>. Required on iOS for daily reminders.
        </p>
      ) : (
        <>
          <p className="install-hint__text">Add 8Bit to your home screen for daily play.</p>
          <button className="btn btn--small" onClick={install}>
            Install
          </button>
        </>
      )}
    </div>
  );
}
