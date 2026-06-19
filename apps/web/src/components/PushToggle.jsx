import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useToast } from './Toast.jsx';

// Convert a base64url VAPID public key into the Uint8Array push wants.
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

function isIos() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent) && !window.MSStream;
}
function isStandalone() {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    window.navigator.standalone === true
  );
}

const pushSupported = () =>
  'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

export default function PushToggle() {
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);
  const [subscribed, setSubscribed] = useState(false);
  const [supported] = useState(pushSupported());

  // Reflect existing subscription state.
  useEffect(() => {
    if (!supported) return;
    let alive = true;
    (async () => {
      try {
        const reg = await navigator.serviceWorker.ready;
        const sub = await reg.pushManager.getSubscription();
        if (alive) setSubscribed(!!sub);
      } catch {
        /* ignore */
      }
    })();
    return () => {
      alive = false;
    };
  }, [supported]);

  const enable = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        toast(
          permission === 'denied'
            ? 'Notifications blocked. Enable them in browser settings.'
            : 'Notification permission not granted.',
          { type: 'warn', duration: 3500 }
        );
        return;
      }

      const reg = await navigator.serviceWorker.ready;
      const { publicKey } = await api.vapidKey();
      if (!publicKey) throw new Error('No VAPID key from server');

      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      });

      // Serialize keys to base64 strings per the contract.
      const json = sub.toJSON();
      await api.subscribePush({
        endpoint: sub.endpoint,
        keys: {
          p256dh: json.keys?.p256dh,
          auth: json.keys?.auth,
        },
      });

      setSubscribed(true);
      toast('Daily reminder enabled! 🔔', { type: 'success' });
    } catch (err) {
      toast(err.message || 'Could not enable reminders', { type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  const disable = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      if (sub) await sub.unsubscribe();
      setSubscribed(false);
      toast('Reminders turned off.', { type: 'info' });
    } catch (err) {
      toast(err.message || 'Could not disable reminders', { type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  // iOS: web push only works for installed PWAs.
  if (isIos() && !isStandalone()) {
    return (
      <div className="push-row push-row--hint">
        <span>
          To get daily reminders on iPhone, tap <strong>Share</strong> →{' '}
          <strong>Add to Home Screen</strong>, then open 8Bit from your home screen.
        </span>
      </div>
    );
  }

  if (!supported) {
    return (
      <div className="push-row push-row--hint">
        <span>Push notifications aren’t supported in this browser.</span>
      </div>
    );
  }

  return (
    <div className="push-row">
      <div className="push-row__text">
        <strong>Daily reminder</strong>
        <span className="push-row__sub">
          {subscribed ? 'On — we’ll ping you each day.' : 'Get nudged to keep your streak.'}
        </span>
      </div>
      <button
        type="button"
        className={'toggle' + (subscribed ? ' toggle--on' : '')}
        onClick={subscribed ? disable : enable}
        disabled={busy}
        aria-pressed={subscribed}
        aria-label="Toggle daily reminder"
      >
        <span className="toggle__knob" />
      </button>
    </div>
  );
}
