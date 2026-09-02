import WebApp from '@twa-dev/sdk';

/**
 * Sessiya identifikatori va platforma.
 *
 * Alohida modulda: `api/client` ham, `analytics/track` ham bunga muhtoj,
 * lekin bir-birini import qilsa aylanma bog'liqlik chiqadi.
 *
 * `session_id` Mini App ochilganda bir marta generatsiya qilinadi va
 * `sessionStorage`da turadi (§6.2).
 */
const SESSION_KEY = 'pochta.sessionId';

let cached: string | null = null;

function createId(): string {
  try {
    return crypto.randomUUID();
  } catch {
    // Juda eski webview — UUID'ga o'xshash qiymat yetarli, u faqat
    // event'larni bir sessiyaga bog'lash uchun ishlatiladi.
    return `00000000-0000-4000-8000-${Date.now().toString(16).padStart(12, '0').slice(-12)}`;
  }
}

export function getSessionId(): string {
  if (cached) {
    return cached;
  }
  try {
    const existing = sessionStorage.getItem(SESSION_KEY);
    if (existing) {
      cached = existing;
      return existing;
    }
    const created = createId();
    sessionStorage.setItem(SESSION_KEY, created);
    cached = created;
    return created;
  } catch {
    // Private rejim yoki storage o'chirilgan.
    cached = createId();
    return cached;
  }
}

export function getPlatform(): string {
  try {
    return WebApp.platform || 'unknown';
  } catch {
    return 'unknown';
  }
}
