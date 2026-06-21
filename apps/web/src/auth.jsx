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

  const login = useCallback(
    async ({ rollNumber, password }) => {
      const data = await api.login({ rollNumber, password });
      applyAuth(data.accessToken, data.user);
      return data.user;
    },
    [applyAuth]
  );

  const register = useCallback(
    async ({ rollNumber, username, email, password }) => {
      const data = await api.register({ rollNumber, username, email, password });
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
      login,
      register,
      logout,
      refreshUser,
      setUser,
    }),
    [token, user, isEditor, login, register, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}
