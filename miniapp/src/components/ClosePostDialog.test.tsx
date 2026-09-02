import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ClosePostDialog } from './ClosePostDialog';
import { api } from '../api/endpoints';
import { uz } from '../i18n/uz';

/**
 * E'lonni yopish oqimi (§6.4, 3-band).
 *
 * Qo'riqlanadigan narsa — uchta sabab alohida qolishi. Kimdir ularni
 * "yopish" bitta tugmasiga soddalashtirsa, fill rate va "javob bo'lmadi"
 * ulushini hisoblab bo'lmay qoladi.
 */
vi.mock('../api/endpoints', () => ({
  api: { closePost: vi.fn() },
}));

vi.mock('../hooks/useTelegram', () => ({
  haptic: vi.fn(),
  hapticSuccess: vi.fn(),
}));

const POST_ID = 'aaaa1111-0000-4000-8000-000000000009';

const CLOSED_POST = { id: POST_ID, status: 'CLOSED' };

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('ClosePostDialog', () => {
  beforeEach(() => {
    vi.mocked(api.closePost).mockReset();
  });

  it('uchta sabab ham ko‘rsatiladi', () => {
    render(<ClosePostDialog postId={POST_ID} onClose={() => undefined} />, { wrapper });

    expect(screen.getByRole('button', { name: uz.close.found })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: uz.close.cancelled })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: uz.close.noAnswer })).toBeInTheDocument();
  });

  it('sabab tanlanmaguncha yopish tugmasi o‘chiq', () => {
    render(<ClosePostDialog postId={POST_ID} onClose={() => undefined} />, { wrapper });

    expect(screen.getByRole('button', { name: uz.close.submit })).toBeDisabled();
  });

  it('"hech kim yozmadi" NO_ANSWER bo‘lib yuboriladi', async () => {
    vi.mocked(api.closePost).mockResolvedValue(CLOSED_POST as never);
    const user = userEvent.setup();
    render(<ClosePostDialog postId={POST_ID} onClose={() => undefined} />, { wrapper });

    await user.click(screen.getByRole('button', { name: uz.close.noAnswer }));
    await user.click(screen.getByRole('button', { name: uz.close.submit }));

    await waitFor(() => {
      expect(api.closePost).toHaveBeenCalledWith(POST_ID, { reason: 'NO_ANSWER' });
    });
    expect(await screen.findByText(uz.close.done)).toBeInTheDocument();
  });

  it('"odam topildi" FOUND bo‘lib yuboriladi va onClosed chaqiriladi', async () => {
    vi.mocked(api.closePost).mockResolvedValue(CLOSED_POST as never);
    const onClosed = vi.fn();
    const user = userEvent.setup();
    render(
      <ClosePostDialog postId={POST_ID} onClose={() => undefined} onClosed={onClosed} />,
      { wrapper },
    );

    await user.click(screen.getByRole('button', { name: uz.close.found }));
    await user.click(screen.getByRole('button', { name: uz.close.submit }));

    await waitFor(() => {
      expect(api.closePost).toHaveBeenCalledWith(POST_ID, { reason: 'FOUND' });
    });
    expect(onClosed).toHaveBeenCalledTimes(1);
  });

  it('bekor qilish oynani yopadi, so‘rov yuborilmaydi', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ClosePostDialog postId={POST_ID} onClose={onClose} />, { wrapper });

    await user.click(screen.getByRole('button', { name: uz.common.cancel }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(api.closePost).not.toHaveBeenCalled();
  });
});
