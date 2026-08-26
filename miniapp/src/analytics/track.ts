import WebApp from '@twa-dev/sdk';
import { beacon } from '../api/client';
import { EVENTS_PATH, api } from '../api/endpoints';
import type { TrackEventPayload } from '../api/types';

/**
 * Event yuborish (CLAUDE.md §6.2).
 *
 * Qoidalar:
 *  - batch: 10 event yoki 5 soniya
 *  - yozilmasa ilova ishlashda davom etadi (xato yutiladi)
 *  - `session_id` bir marta generatsiya qilinadi va `sessionStorage`da turadi
 *  - PII yuborilmaydi (backend ham filtrlaydi, lekin manbadan boshlanadi — §1.7)
 */

const BATCH_SIZE = 10;
const FLUSH_INTERVAL_MS = 5000;
const SESSION_KEY = 'pochta.sessionId';

let queue: TrackEventPayload[] = [];
let timer: ReturnType<typeof setTimeout> | null = null;

function readSessionId(): string {
  try {
    const existing = sessionStorage.getItem(SESSION_KEY);
    if (existing) {
      return existing;
    }
    const created = crypto.randomUUID();
    sessionStorage.setItem(SESSION_KEY, created);
    return created;
  } catch {
    // Private rejim yoki storage o'chirilgan — sessiya faqat shu sahifa uchun.
    return crypto.randomUUID();
  }
}

let sessionId: string | null = null;

export function getSessionId(): string {
  if (!sessionId) {
    sessionId = readSessionId();
  }
  return sessionId;
}

export function getPlatform(): string {
  return WebApp.platform || 'unknown';
}

export function track(name: string, properties?: Record<string, unknown>, postId?: string): void {
  queue.push({
    name,
    sessionId: getSessionId(),
    platform: getPlatform(),
    occurredAt: new Date().toISOString(),
    ...(postId ? { postId } : {}),
    ...(properties ? { properties } : {}),
  });

  if (queue.length >= BATCH_SIZE) {
    void flush();
    return;
  }
  if (!timer) {
    timer = setTimeout(() => void flush(), FLUSH_INTERVAL_MS);
  }
}

export async function flush(): Promise<void> {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  if (queue.length === 0) {
    return;
  }
  const batch = queue;
  queue = [];
  try {
    await api.sendEvents(batch);
  } catch {
    // Analitika yo'qolishi so'rovni buzmaydi (§6.2). Qayta urinmaymiz —
    // takroriy event metrikani buzadi.
  }
}

/** Ilova yopilayotganda navbatni tashlab ketmaslik uchun. */
export function flushOnUnload(): void {
  if (queue.length === 0) {
    return;
  }
  const batch = queue;
  queue = [];
  beacon(EVENTS_PATH, { events: batch });
}

/** Faqat testlar uchun. */
export function __resetQueue(): void {
  queue = [];
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
}
