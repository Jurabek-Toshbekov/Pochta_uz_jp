import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Step4Contact } from './Step4Contact';
import { api } from '../../api/endpoints';
import { uz } from '../../i18n/uz';
import { usePostFormStore } from '../../store/postFormStore';

/**
 * Aloqa qadami (§9.2).
 *
 * Qo'riqlanadigan narsa: username SERVERdan olinadi. Ba'zi Telegram
 * mijozlarida `initDataUnsafe` bo'sh keladi va faqat unga tayangan kod
 * maydonni bo'sh qoldiradi — aynan shu xato bo'lgan. Shuning uchun bu
 * testda `telegramUser()` ataylab `null` qaytaradi.
 */
vi.mock('../../api/endpoints', () => ({
  api: { openSession: vi.fn() },
  EVENTS_PATH: '/api/miniapp/events',
}));

vi.mock('../../hooks/useTelegram', () => ({
  haptic: vi.fn(),
  telegramUser: () => null,
}));

const SESSION = {
  username: 'Jurabek_Toshbekov',
  firstName: 'Jurabek',
  uiLanguage: 'uz',
  role: 'USER',
  verificationLevel: 'NONE',
  trustScore: 0,
  needsConsent: false,
  phoneVerified: false,
  startParam: null,
  isNewUser: false,
  serverTime: '2026-09-02T00:00:00Z',
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('Step4Contact', () => {
  beforeEach(() => {
    usePostFormStore.getState().reset();
    vi.mocked(api.openSession).mockReset().mockResolvedValue(SESSION as never);
  });

  it('username serverdagi sessiyadan to‘ladi (initDataUnsafe bo‘sh bo‘lsa ham)', async () => {
    render(<Step4Contact fieldErrors={{}} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByLabelText(uz.step4.telegram)).toHaveValue('Jurabek_Toshbekov');
    });
    expect(usePostFormStore.getState().contactTelegram).toBe('Jurabek_Toshbekov');
  });

  it('foydalanuvchi yozgan username bosib ketilmaydi', async () => {
    usePostFormStore.getState().patch({ contactTelegram: 'boshqa_nom' });

    render(<Step4Contact fieldErrors={{}} />, { wrapper });

    await waitFor(() => expect(api.openSession).toHaveBeenCalled());
    expect(usePostFormStore.getState().contactTelegram).toBe('boshqa_nom');
  });

  it('username hech qayerda bo‘lmasa — nima qilish kerakligi aytiladi', async () => {
    vi.mocked(api.openSession).mockResolvedValue({ ...SESSION, username: null } as never);

    render(<Step4Contact fieldErrors={{}} />, { wrapper });

    expect(await screen.findByText(uz.step4.noUsername)).toBeInTheDocument();
  });

  it('ikkala maydon ham majburiy deb belgilangan', async () => {
    render(<Step4Contact fieldErrors={{}} />, { wrapper });

    expect(
      screen.getByText(`${uz.step4.telegram} (${uz.common.required})`),
    ).toBeInTheDocument();
    expect(screen.getByText(`${uz.step4.phone} (${uz.common.required})`)).toBeInTheDocument();
    expect(screen.getByText(uz.step4.bothRequired)).toBeInTheDocument();
  });
});
