import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EditPostPage } from './EditPostPage';
import { api } from '../api/endpoints';
import { uz } from '../i18n/uz';

/**
 * Tahrirlash ekrani (§9.1).
 *
 * Ikkita qoida qo'riqlanadi: tur/yo'nalish/aeroport hech qachon so'rovga
 * qo'shilmaydi (ular o'zgarsa ko'rishlar va kanaldagi post eskisiga
 * bog'lanib qoladi, metrikalar buziladi) va yopilgan e'lon tahrirlanmaydi.
 */
vi.mock('../api/endpoints', () => ({
  api: { myPost: vi.fn(), reference: vi.fn(), updatePost: vi.fn() },
  EVENTS_PATH: '/api/miniapp/events',
}));

/**
 * Saqlash — Telegram `MainButton`i, DOM'da tugma yo'q (§9.3: o'z tugmangizni
 * yasamang). Shu sabab test uning `onClick`ini ushlab turadi.
 */
const mainButton = vi.hoisted(() => ({
  onClick: null as null | (() => void),
  text: '',
  enabled: true,
}));

vi.mock('../hooks/useTelegram', () => ({
  haptic: vi.fn(),
  hapticSuccess: vi.fn(),
  useBackButton: vi.fn(),
  useMainButton: (options: { text: string; onClick: () => void; enabled?: boolean }) => {
    mainButton.onClick = options.onClick;
    mainButton.text = options.text;
    mainButton.enabled = options.enabled ?? true;
  },
}));

const POST_ID = 'aaaa1111-0000-4000-8000-00000000000e';

const REFERENCE = {
  airports: [],
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
    {
      id: 2,
      code: 'CLOTHES',
      titleUz: 'Kiyim',
      titleRu: 'Одежда',
      emoji: '👕',
      riskLevel: 'LOW',
      warningUz: null,
      sortOrder: 20,
    },
  ],
  corridors: [],
};

const POST = {
  id: POST_ID,
  postType: 'CARRY',
  direction: 'JP_UZ',
  originAirport: 'NRT',
  destAirport: 'TAS',
  originCityFree: null,
  destCityFree: null,
  finalDestination: null,
  departDate: '2027-01-15',
  deadlineDate: null,
  dateFlexibleDays: 0,
  weightKg: 5,
  weightKgMax: null,
  priceAmount: 2000,
  priceCurrency: 'JPY',
  priceUnit: 'PER_KG',
  categoryIds: [1],
  comment: 'Hujjat olib ketaman',
  contactPhone: null,
  contactTelegram: 'testuser',
  contactOther: null,
  status: 'PUBLISHED',
  channelMessageId: 9001,
  channelUrl: null,
  deepLink: null,
  publishedAt: '2026-09-01T00:00:00Z',
  expiresAt: '2027-01-17T00:00:00Z',
  viewCount: 3,
  contactRevealCount: 1,
  createdAt: '2026-09-01T00:00:00Z',
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/my/${POST_ID}/edit`]}>
        <Routes>
          <Route path="/my/:postId/edit" element={<EditPostPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function save() {
  await act(async () => {
    mainButton.onClick?.();
  });
}

/** Oxirgi `updatePost` chaqiruvidagi tana. */
function lastBody() {
  const calls = vi.mocked(api.updatePost).mock.calls;
  const last = calls[calls.length - 1];
  if (!last) {
    throw new Error('updatePost chaqirilmadi');
  }
  return last[1];
}

describe('EditPostPage', () => {
  beforeEach(() => {
    mainButton.onClick = null;
    vi.mocked(api.myPost).mockReset().mockResolvedValue(POST as never);
    vi.mocked(api.reference).mockReset().mockResolvedValue(REFERENCE as never);
    vi.mocked(api.updatePost).mockReset().mockResolvedValue(POST as never);
  });

  it('mavjud qiymatlar bilan to‘ldiriladi', async () => {
    renderPage();

    expect(await screen.findByLabelText(uz.step3.comment)).toHaveValue('Hujjat olib ketaman');
    expect(screen.getByLabelText(uz.edit.dateCarry)).toHaveValue('2027-01-15');
    expect(screen.getByLabelText(uz.step3.price)).toHaveValue(2000);
    expect(screen.getByLabelText(uz.step4.telegram)).toHaveValue('testuser');
  });

  it('tur, yo‘nalish va aeroport so‘rovga qo‘shilmaydi', async () => {
    const user = userEvent.setup();
    renderPage();

    const price = await screen.findByLabelText(uz.step3.price);
    await user.clear(price);
    await user.type(price, '3000');
    await save();

    await waitFor(() => expect(api.updatePost).toHaveBeenCalled());

    const body = lastBody();
    expect(body.priceAmount).toBe(3000);
    expect(body).not.toHaveProperty('postType');
    expect(body).not.toHaveProperty('direction');
    expect(body).not.toHaveProperty('originAirport');
    expect(body).not.toHaveProperty('destAirport');
  });

  it('izoh tozalansa bo‘sh satr yuboriladi — "tegmang" emas', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(await screen.findByLabelText(uz.step3.comment));
    await save();

    await waitFor(() => expect(api.updatePost).toHaveBeenCalled());
    expect(lastBody().comment).toBe('');
  });

  it('"Kelishamiz" tanlansa narx maydoni yo‘qoladi va summa yuborilmaydi', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('radio', { name: uz.step3.unitNegotiable }));
    expect(screen.queryByLabelText(uz.step3.price)).not.toBeInTheDocument();

    await save();
    await waitFor(() => expect(api.updatePost).toHaveBeenCalled());

    const body = lastBody();
    expect(body.priceUnit).toBe('NEGOTIABLE');
    expect(body).not.toHaveProperty('priceAmount');
  });

  it('saqlangach tasdiq ko‘rsatiladi', async () => {
    renderPage();
    await screen.findByLabelText(uz.step3.comment);

    await save();

    expect(await screen.findByText(uz.edit.saved)).toBeInTheDocument();
  });

  it('yopilgan e‘lon tahrirlanmaydi — sabab aytiladi', async () => {
    vi.mocked(api.myPost).mockResolvedValue({ ...POST, status: 'CLOSED' } as never);
    renderPage();

    expect(await screen.findByText(uz.edit.notEditable)).toBeInTheDocument();
    expect(screen.queryByLabelText(uz.step3.comment)).not.toBeInTheDocument();
  });
});
