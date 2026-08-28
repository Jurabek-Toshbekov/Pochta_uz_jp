import type { ApiError, LoginResponse } from './types';

/**
 * Admin API klienti (§11.1, §12).
 *
 * Ikkita ish qiladi:
 *  - har bir so'rovga `Authorization: Bearer <access>` qo'shadi;
 *  - access muddati tugab 403 kelsa, refresh bilan bir marta yangilaydi va
 *    so'rovni takrorlaydi. Foydalanuvchi 2 soatda bir marta chiqib
 *    ketmasligi kerak.
 *
 * Token `localStorage`da: admin paneli bitta ish stolida ochiladi va
 * sahifa yangilanganda qayta kirish talab qilinmasligi kerak.
 */

const ACCESS_KEY = 'pochta.admin.access';
const REFRESH_KEY = 'pochta.admin.refresh';

/** Bo'sh bo'lsa nisbiy yo'l — Vite dev proxy backendga uzatadi. */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiRequestError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, error: ApiError) {
    super(error.message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = error.code;
    this.fieldErrors = error.fieldErrors ?? {};
  }
}

export const tokenStore = {
  access(): string | null {
    return safeRead(ACCESS_KEY);
  },
  refresh(): string | null {
    return safeRead(REFRESH_KEY);
  },
  save(login: LoginResponse): void {
    safeWrite(ACCESS_KEY, login.accessToken);
    safeWrite(REFRESH_KEY, login.refreshToken);
  },
  clear(): void {
    safeRemove(ACCESS_KEY);
    safeRemove(REFRESH_KEY);
  },
};

/** `localStorage` maxfiy rejimda yoki bloklangan brauzerda xato beradi. */
function safeRead(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeWrite(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Token faqat shu sessiyada yashaydi — bu ham ishlaydi.
  }
}

function safeRemove(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // e'tiborsiz
  }
}

/** Sessiya tugaganda ilova kirish ekraniga qaytishi uchun. */
type SessionExpiredHandler = () => void;
let onSessionExpired: SessionExpiredHandler = () => undefined;

export function setSessionExpiredHandler(handler: SessionExpiredHandler): void {
  onSessionExpired = handler;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Kirish oqimi: token qo'shilmaydi va 403 da refresh urinilmaydi. */
  anonymous?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);

  if (response.status === 403 && !options.anonymous && tokenStore.refresh()) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      const retry = await send(path, options);
      return handle<T>(retry);
    }
    tokenStore.clear();
    onSessionExpired();
  }

  return handle<T>(response);
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const access = tokenStore.access();
  if (!options.anonymous && access) {
    headers.Authorization = `Bearer ${access}`;
  }

  return fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

async function handle<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const parsed: unknown = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const error = (parsed ?? {}) as ApiError;
    throw new ApiRequestError(response.status, {
      code: error.code ?? 'UNKNOWN',
      message: error.message ?? 'Xatolik yuz berdi. Birozdan keyin qayta urinib ko’ring.',
      fieldErrors: error.fieldErrors,
    });
  }
  return parsed as T;
}

/** Bir marta urinadi: muvaffaqiyatsiz bo'lsa sessiya tugagan hisoblanadi. */
async function tryRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) {
    return false;
  }
  try {
    const response = await fetch(`${BASE_URL}/api/admin/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
      return false;
    }
    const login = (await response.json()) as LoginResponse;
    tokenStore.save(login);
    return true;
  } catch {
    return false;
  }
}

/** Query string yasash: bo'sh qiymatlar tashlab ketiladi. */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    search.set(key, String(value));
  }
  const result = search.toString();
  return result ? `?${result}` : '';
}
