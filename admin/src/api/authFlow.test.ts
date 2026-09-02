import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { request, tokenStore } from './client';

/**
 * 401 va 403 farqi (§11.1).
 *
 * 401 — kim ekanligi aniqlanmadi (token yo'q, eskirgan): refresh mantiqiy.
 * 403 — huquq yo'q (rol olib qo'yilgan, hisob bloklangan): refresh foydasiz,
 * yangi token ham xuddi shu javobni oladi, shuning uchun darhol chiqariladi.
 *
 * Bu ajratish buzilsa ikki xato yuzaga keladi: yo foydalanuvchi har 2 soatda
 * chiqib ketadi, yo huquqi olib qo'yilgan odam cheksiz qayta urinaveradi.
 */
describe('admin klienti — 401/403', () => {
  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

  beforeEach(() => {
    localStorage.clear();
    tokenStore.save({ accessToken: 'eski', refreshToken: 'refresh-1' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('401 kelsa tokenni yangilaydi va so’rovni takrorlaydi', async () => {
    const calls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      calls.push(String(url));
      if (String(url).endsWith('/api/admin/auth/refresh')) {
        return json(200, { accessToken: 'yangi', refreshToken: 'refresh-2', role: 'ADMIN' });
      }
      return calls.filter((c) => c.endsWith('/api/admin/overview')).length === 1
        ? json(401, { code: 'UNAUTHORIZED', message: 'Token yaroqsiz.' })
        : json(200, { todayPosts: 3 });
    }));

    await expect(request('/api/admin/overview')).resolves.toEqual({ todayPosts: 3 });
    expect(calls.filter((c) => c.endsWith('/api/admin/overview'))).toHaveLength(2);
    expect(tokenStore.access()).toBe('yangi');
  });

  it('403 kelsa yangilamaydi — tokenni tozalaydi', async () => {
    const calls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      calls.push(String(url));
      return json(403, { code: 'FORBIDDEN', message: 'Huquq bekor qilingan.' });
    }));

    await expect(request('/api/admin/overview')).rejects.toThrow();
    expect(calls.some((c) => c.endsWith('/api/admin/auth/refresh'))).toBe(false);
    expect(tokenStore.access()).toBeNull();
  });
});
