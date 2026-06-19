# 8Bit Daily Puzzle — Web (PWA)

A mobile-first, installable PWA for the 8Bit daily Wordle-style college game.
React 18 + Vite + JavaScript (no TypeScript), `react-router-dom` v6, `vite-plugin-pwa`.

## Prerequisites

- Node 20+, npm 10+

## Install & run

```bash
npm install
npm run dev      # starts Vite with --host (LAN reachable) on port 5173
```

Other scripts:

```bash
npm run build    # production build to /dist
npm run preview  # preview the production build (also --host)
```

## Configure the backend URL

The app reads the API base from `import.meta.env.VITE_API_BASE` (default
`http://localhost:8080`).

1. Copy `.env.example` to `.env`:

   ```bash
   cp .env.example .env
   ```

2. Set it to your backend, e.g.:

   ```
   VITE_API_BASE=http://192.168.1.42:8080
   ```

   Restart `npm run dev` after changing `.env`.

All authenticated requests send `Authorization: Bearer <accessToken>` (token kept
in `localStorage`). On any `401` the app clears auth and redirects to `/login`.

## Testing on a phone over the LAN

1. Make sure the phone and your PC are on the **same Wi-Fi**.
2. The dev server is started with `--host`, so it binds to your LAN IP. Find your
   PC's LAN IP:
   - Windows: `ipconfig` → look for the IPv4 Address (e.g. `192.168.1.42`).
3. On the phone, open `http://<your-PC-LAN-IP>:5173`.
4. **Set `VITE_API_BASE` to a value the phone can reach** — `localhost` on the
   phone is the phone itself, so point it at your PC's LAN IP (e.g.
   `http://192.168.1.42:8080`).
5. Ensure the **backend CORS** allows the web origin
   (`http://<your-PC-LAN-IP>:5173`) and the `Authorization` header.
6. Windows Firewall may prompt the first time — allow Node on private networks.

> Note: real web push and "Add to Home Screen" install behave best over HTTPS.
> On iOS, web push only works when the app is installed to the Home Screen.

## PWA icons

The manifest currently references a **placeholder SVG** at
`public/icons/icon.svg` so the build works without binary assets.

For production, drop real PNG icons into `public/icons/`:

- `public/icons/icon-192.png` (192×192)
- `public/icons/icon-512.png` (512×512)
- `public/icons/maskable-512.png` (512×512, maskable, safe-zone padding)

Then enable the PNG entries in `vite.config.js` (the `icons` array in the
`VitePWA(... manifest ...)` block has them commented out, ready to uncomment).

## Project structure

```
apps/web/
├─ index.html
├─ package.json
├─ vite.config.js
├─ .env.example
├─ public/
│  └─ icons/icon.svg
└─ src/
   ├─ main.jsx
   ├─ App.jsx
   ├─ api.js
   ├─ auth.jsx
   ├─ styles.css
   ├─ components/
   │  ├─ WordleGrid.jsx
   │  ├─ Keyboard.jsx
   │  ├─ BatchWarBar.jsx
   │  ├─ ResultModal.jsx
   │  ├─ Toast.jsx
   │  ├─ NavBar.jsx
   │  ├─ InstallHint.jsx
   │  ├─ PushToggle.jsx
   │  └─ ProtectedRoute.jsx
   └─ pages/
      ├─ LoginPage.jsx
      ├─ RegisterPage.jsx
      ├─ HomePage.jsx
      ├─ PlayPage.jsx
      ├─ LeaderboardPage.jsx
      ├─ ProfilePage.jsx
      ├─ UserPage.jsx
      └─ AdminPage.jsx
```

## Routes

`/login`, `/register` (public) · `/`, `/play`, `/leaderboard`, `/profile`,
`/u/:username` (protected) · `/admin` (protected + requires `ROLE_EDITOR` or
`ROLE_ADMIN`).

## Accessibility

Tile colours are paired with symbols (`✓` correct, `~` present, `·` absent) and
ARIA labels so the game is usable without relying on colour alone.
```
