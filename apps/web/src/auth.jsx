import { createContext, useContext, useEffect, useMemo, useState, useCallback } from 'react';
import {
  api,
  getToken,
  setToken,
  getStoredUser,
  setStoredUser,
  setUnauthorizedHandler,
} from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => getToken());
  const [user, setUser] = useState(() => getStoredUser());

  // Persist + apply token.
  const applyAuth = useCallback((accessToken, userObj) => {
    setToken(accessToken);
    setStoredUser(userObj);
    setTokenState(accessToken || null);
    setUser(userObj || null);
  }, []);

  const logout = useCallback(() => {
    applyAuth(null, null);
  }, [applyAuth]);

  // On a 401 from any request, clear auth. The router will then bounce to /login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      applyAuth(null, null);
    });
    return () => setUnauthorizedHandler(null);
  }, [applyAuth]);

  // If a response carries a token, it means OTP was off (or the code was just verified) and we're
  // logged in. Otherwise otpRequired is true and the page should collect the emailed code.
  const applyIfToken = useCallback(
    (data) => {
      if (data?.accessToken) applyAuth(data.accessToken, data.user);
      return data;
    },
    [applyAuth]
  );

  // Step 1 for sign-up + login: email + roll + username; a code is emailed. Returns { otpRequired }.
  const start = useCallback(
    async ({ email, rollNumber, username }) =>
      applyIfToken(await api.start({ email, rollNumber, username })),
    [applyIfToken]
  );

  // Step 2: verify the emailed code. Logs in on success.
  const verifyCode = useCallback(
    async (email, code) => {
      const data = await api.verifyCode(email, code);
      applyAuth(data.accessToken, data.user);
      return data.user;
    },
    [applyAuth]
  );

  // Refresh the cached user (e.g. after profile / streak changes).
  const refreshUser = useCallback(async () => {
    try {
      const data = await api.me();
      if (data && data.user) {
        setStoredUser(data.user);
        setUser(data.user);
      }
      return data;
    } catch {
      return null;
    }
  }, []);

  const isEditor =
    !!user &&
    Array.isArray(user.roles) &&
    user.roles.some((r) => r === 'ROLE_EDITOR' || r === 'ROLE_ADMIN');

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: !!token,
      isEditor,
      start,
      verifyCode,
      logout,
      refreshUser,
      setUser,
    }),
    [token, user, isEditor, start, verifyCode, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}
