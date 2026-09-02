import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NewPostPage } from './NewPostPage';
import { uz } from '../i18n/uz';
import { ru } from '../i18n/ru';
import { uzCyrl } from '../i18n/uz-cyrl';

const REFERENCE = { airports: [], categories: [], corridors: [] };

function mockFetch() {
  globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.includes('/reference')
      ? REFERENCE
      : url.includes('/drafts')
        ? { step: null, payload: {}, updatedAt: null }
        : {};
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }) as unknown as typeof fetch;
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/new']}>
        <NewPostPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/**
 * Pastki maslahat keyingi qadamga o'tish uchun NIMA YETISHMAYOTGANINI
 * aytadi. Ilgari u sarlavhani takrorlardi ("Nima qilmoqchisiz?" ekranda
 * ikki marta) — bu foydalanuvchiga hech narsa bermaydi (§9.4).
 */
describe('NewPostPage — pastki maslahat', () => {
  beforeEach(() => {
    mockFetch();
  });

  it('sarlavhani takrorlamaydi', async () => {
    renderPage();
    expect(await screen.findByText(uz.step1.incomplete)).toBeTruthy();
    expect(screen.queryAllByText(uz.step1.title)).toHaveLength(1);
  });

  it('uchala tilda maslahat bor va sarlavhadan farq qiladi', () => {
    for (const dict of [uz, uzCyrl, ru]) {
      expect(dict.step1.incomplete.length).toBeGreaterThan(5);
      expect(dict.step2.incomplete.length).toBeGreaterThan(5);
      expect(dict.step1.incomplete).not.toBe(dict.step1.title);
      expect(dict.step2.incomplete).not.toBe(dict.step2.title);
    }
  });
});
