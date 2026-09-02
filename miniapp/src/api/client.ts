import WebApp from '@twa-dev/sdk';
import { getPlatform, getSessionId } from '../session';
import type { ApiErrorBody } from './types';

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

/**
 * Backend xatosi. `fieldErrors` bo'lsa forma aynan o'sha inputni belgilaydi (§9.4).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? 'Xatolik yuz berdi.');
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN';
    this.fieldErrors = body.fieldErrors ?? {};
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isRateLimited(): boolean {
    return this.status === 429;
  }
}

/** Tarmoq uzilgan holat — "Qayta urinish" tugmasi ko'rsatiladi (§9.5). */
export class NetworkError extends Error {
  constructor() {
    super('Internet aloqasi yo‘q. Qayta urinib ko‘ring.');
    this.name = 'NetworkError';
  }
}

/**
 * Lokal ishlab chiqish uchun tayyor `initData`.
 *
 * Telegram tashqarisida `WebApp.initData` bo'sh bo'ladi va API 401 qaytaradi.
 * Brauzerda UI'ni sinash uchun `.env.local`ga `VITE_DEV_INIT_DATA` qo'yiladi
 * (`scripts/dev-initdata.py` yasab beradi).
 *
 * Bu tekshiruvni CHETLAB O'TMAYDI: satr backendda xuddi shu HMAC imzosi
 * bilan tekshiriladi (§7.1). Faqat uni Telegramsiz yetkazib berish usuli.
 * `import.meta.env.DEV` false bo'lganda Vite bu kodni butunlay olib tashlaydi,
 * ya'ni prod bundle'da mavjud emas.
 */
const DEV_INIT_DATA: string = import.meta.env.DEV
  ? (import.meta.env.VITE_DEV_INIT_DATA ?? '')
  : '';

/**
 * Har bir so'rovga `Authorization: tma <initData>` qo'shiladi (§7.1).
 * Boshqa autentifikatsiya usuli yo'q — user_id hech qachon body'da yuborilmaydi.
 */
function authHeader(): Record<string, string> {
  const initData = WebApp.initData || DEV_INIT_DATA;
  return initData ? { Authorization: `tma ${initData}` } : {};
}

/**
 * Sessiya konteksti header'da ketadi, query'da emas: aks holda u kesh
 * kalitiga tushib qolardi va `search_queries` tahlilini chalkashtirardi.
 */
function contextHeaders(): Record<string, string> {
  return { 'X-Session-Id': getSessionId(), 'X-Platform': getPlatform() };
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options;

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      signal,
      headers: {
        ...authHeader(),
        ...contextHeaders(),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new NetworkError();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed: unknown = text ? safeParse(text) : null;

  if (!response.ok) {
    throw new ApiError(response.status, (parsed ?? {}) as Partial<ApiErrorBody>);
  }
  return parsed as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** `navigator.sendBeacon` uchun — ilova yopilayotganda event yuborish. */
export function beacon(path: string, payload: unknown): boolean {
  if (typeof navigator === 'undefined' || !navigator.sendBeacon) {
    return false;
  }
  // sendBeacon header qo'shishga ruxsat bermaydi, shuning uchun initData
  // query parametri sifatida YUBORILMAYDI (§7.1 — sir URL'da bo'lmaydi).
  // Buning o'rniga oddiy fetch keepAlive ishlatiladi.
  try {
    void fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      keepalive: true,
      headers: { ...authHeader(), ...contextHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return true;
  } catch {
    return false;
  }
}
