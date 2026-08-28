import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { setSessionExpiredHandler, tokenStore } from '../api/client';
import { api } from '../api/endpoints';
import type { UserRole } from '../api/types';

/**
 * Admin sessiyasi (§11.1).
 *
 * <p>Server holati emas — shuning uchun TanStack Query'da emas, oddiy
 * context'da. Token bor/yo'qligi ilovaning ikkiga bo'linishini belgilaydi:
 * kirish ekrani yoki panel.
 */

interface AuthState {
  authenticated: boolean;
  role: UserRole | null;
  userId: string | null;
  login: (code: string) => Promise<void>;
  logout: () => void;
}

const ROLE_KEY = 'pochta.admin.role';
const USER_KEY = 'pochta.admin.user';

const AuthContext = createContext<AuthState | null>(null);

function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key);
    } else {
      window.localStorage.setItem(key, value);
    }
  } catch {
    // e'tiborsiz — sessiya baribir ishlaydi
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState<boolean>(() => tokenStore.access() !== null);
  const [role, setRole] = useState<UserRole | null>(() => readStored(ROLE_KEY) as UserRole | null);
  const [userId, setUserId] = useState<string | null>(() => readStored(USER_KEY));

  const logout = useCallback(() => {
    tokenStore.clear();
    writeStored(ROLE_KEY, null);
    writeStored(USER_KEY, null);
    setAuthenticated(false);
    setRole(null);
    setUserId(null);
  }, []);

  // Refresh ham eskirsa klient shu qo'ng'iroqni qiladi.
  useEffect(() => {
    setSessionExpiredHandler(logout);
  }, [logout]);

  const login = useCallback(async (code: string) => {
    const response = await api.login(code);
    tokenStore.save(response);
    writeStored(ROLE_KEY, response.role);
    writeStored(USER_KEY, response.userId);
    setRole(response.role);
    setUserId(response.userId);
    setAuthenticated(true);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ authenticated, role, userId, login, logout }),
    [authenticated, role, userId, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth faqat AuthProvider ichida ishlaydi');
  }
  return context;
}
