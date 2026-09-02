import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SearchPage } from './SearchPage';
import { useSearchStore } from '../store/searchStore';
import { uz } from '../i18n/uz';

/**
 * Natijasiz qidiruvda obuna taklifi (§6.4, 4-band) — bu "oltin ma'lumot"
 * yig'ish mexanizmi, shuning uchun test bilan qo'riqlanadi.
 */
const REFERENCE = {
  airports: [
    {
      code: 'NRT',
      countryCode: 'JP',
      cityUz: 'Tokio',
      cityRu: 'Токио',
      cityEn: 'Tokyo',
      nameEn: 'Narita',
      latitude: null,
      longitude: null,
      popular: true,
      sortOrder: 10,
    },
  ],
  categories: [
    {
      id: 1,
      code: 'DOCUMENTS',
      titleUz: 'Hujjatlar',
      titleRu: 'Документы',
      emoji: '📄',
      riskLevel: 'LOW',
      warningUz: null,
      sortOrder: 10,
    },
  ],
  corridors: [],
};

const POST = {
  id: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
  postType: 'CARRY',
  direction: 'JP_UZ',
  originAirport: 'NRT',
  destAirport: 'TAS',
  originCityFree: null,
  destCityFree: null,
  finalDestination: null,
  departDate: '2026-12-01',
  deadlineDate: null,
  dateFlexibleDays: 0,
  weightKg: 10,
  weightKgMax: null,
  priceAmount: 2000,
  priceCurrency: 'JPY',
  priceUnit: 'PER_KG',
  categoryIds: [1],
  comment: 'Hujjat olib ketaman',
  verified: false,
  verificationLevel: 'NONE',
  trustScore: 0,
  viewCount: 0,
  contactRevealCount: 0,
  publishedAt: '2026-08-26T10:00:00Z',
  expiresAt: '2026-12-02T23:59:59Z',
};

let searchResponse: unknown = { items: [], latencyMs: 3, totalCount: 0 };
let subscriptionCalls: unknown[] = [];

function mockFetch() {
  globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/reference')) {
      return new Response(JSON.stringify(REFERENCE), { status: 200 });
    }
    if (url.includes('/subscriptions')) {
      subscriptionCalls.push(JSON.parse(String(init?.body ?? '{}')));
      return new Response(JSON.stringify({ id: 'sub-1' }), { status: 201 });
    }
    if (url.includes('/api/miniapp/posts')) {
      return new Response(JSON.stringify(searchResponse), { status: 200 });
    }
    return new Response('', { status: 204 });
  }) as unknown as typeof fetch;
}

function renderSearch() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/search']}>
        <SearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSearchStore.getState().reset();
    subscriptionCalls = [];
    searchResponse = { items: [], latencyMs: 3, totalCount: 0 };
    mockFetch();
  });

  it('natija bo‘lsa karta ko‘rsatiladi', async () => {
    searchResponse = { items: [POST], latencyMs: 5, totalCount: 1 };

    renderSearch();

    expect(await screen.findByText('NRT')).toBeInTheDocument();
    expect(screen.getByText('TAS')).toBeInTheDocument();
    expect(screen.getByText(`1 ${uz.search.results}`)).toBeInTheDocument();
  });

  it('natijasiz qidiruvda taklif chiqadi, lekin shartsiz obuna tugmasi yo‘q', async () => {
    renderSearch();

    expect(await screen.findByText(new RegExp(uz.search.noResults, 'i'))).toBeInTheDocument();
    // Filtr tanlanmagan — obuna qilib bo'lmaydi, tugma ko'rsatilmaydi.
    expect(screen.queryByRole('button', { name: uz.search.subscribeCta })).not.toBeInTheDocument();
  });

  it('yo‘nalish tanlangan natijasiz qidiruv — obuna taklif qilinadi va yoziladi', async () => {
    const user = userEvent.setup();
    useSearchStore.getState().patch({ direction: 'JP_UZ' });

    renderSearch();

    const subscribeButton = await screen.findByRole('button', { name: uz.search.subscribeCta });
    await user.click(subscribeButton);

    await waitFor(() => {
      expect(subscriptionCalls).toHaveLength(1);
    });
    expect(subscriptionCalls[0]).toMatchObject({ direction: 'JP_UZ' });
    expect(await screen.findByText(uz.search.subscribed)).toBeInTheDocument();
  });

  it('natija bor bo‘lsa "qidiruvni saqlash" taklif qilinadi (§10.3)', async () => {
    searchResponse = { items: [POST], latencyMs: 5, totalCount: 1 };
    useSearchStore.getState().patch({ direction: 'JP_UZ' });

    renderSearch();

    expect(await screen.findByRole('button', { name: uz.search.saveSearch })).toBeInTheDocument();
  });

  it('filtrlar paneli ochiladi va faol filtr soni ko‘rinadi', async () => {
    const user = userEvent.setup();
    useSearchStore.getState().patch({ type: 'CARRY' });

    renderSearch();

    const toggle = await screen.findByRole('button', { name: `${uz.search.filters} (1)` });
    await user.click(toggle);

    expect(screen.getByRole('button', { name: uz.search.apply })).toBeInTheDocument();
  });
});
