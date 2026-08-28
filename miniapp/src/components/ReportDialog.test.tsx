import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportDialog } from './ReportDialog';
import { api } from '../api/endpoints';
import { uz } from '../i18n/uz';

/**
 * Shikoyat oqimi (§7.3).
 *
 * <p>Ikkita qoida qo'riqlanadi: sabab tanlanmaguncha yuborib bo'lmaydi
 * va foydalanuvchiga shikoyat e'lonni darhol yopmasligi aytiladi.
 */
vi.mock('../api/endpoints', () => ({
  api: { report: vi.fn() },
}));

vi.mock('../hooks/useTelegram', () => ({
  haptic: vi.fn(),
}));

const POST_ID = 'aaaa1111-0000-4000-8000-000000000001';

describe('ReportDialog', () => {
  beforeEach(() => {
    vi.mocked(api.report).mockReset();
  });

  it('sabab tanlanmaguncha yuborish tugmasi o‘chiq', () => {
    render(<ReportDialog postId={POST_ID} onClose={() => undefined} />);

    expect(screen.getByRole('button', { name: uz.detail.reportSubmit })).toBeDisabled();
  });

  it('shikoyat e‘lonni yopmasligi aytiladi', () => {
    render(<ReportDialog postId={POST_ID} onClose={() => undefined} />);

    expect(screen.getByText(uz.detail.reportHint)).toBeInTheDocument();
  });

  it('sabab tanlangach yuboriladi va tasdiq ko‘rsatiladi', async () => {
    vi.mocked(api.report).mockResolvedValue({
      id: 'r1',
      trustScore: null,
      message: 'ok',
    });
    const user = userEvent.setup();
    render(<ReportDialog postId={POST_ID} onClose={() => undefined} />);

    await user.click(screen.getByRole('button', { name: uz.detail.reportProhibited }));
    await user.click(screen.getByRole('button', { name: uz.detail.reportSubmit }));

    await waitFor(() => {
      expect(api.report).toHaveBeenCalledWith({
        postId: POST_ID,
        reason: 'PROHIBITED',
        details: undefined,
      });
    });
    expect(await screen.findByText(uz.detail.reportSent)).toBeInTheDocument();
  });

  it('izoh yozilsa u ham yuboriladi', async () => {
    vi.mocked(api.report).mockResolvedValue({ id: 'r2', trustScore: null, message: 'ok' });
    const user = userEvent.setup();
    render(<ReportDialog postId={POST_ID} onClose={() => undefined} />);

    await user.click(screen.getByRole('button', { name: uz.detail.reportScam }));
    await user.type(screen.getByLabelText(uz.detail.reportDetails), 'Pul so‘rayapti');
    await user.click(screen.getByRole('button', { name: uz.detail.reportSubmit }));

    await waitFor(() => {
      expect(api.report).toHaveBeenCalledWith({
        postId: POST_ID,
        reason: 'SCAM',
        details: 'Pul so‘rayapti',
      });
    });
  });

  it('bekor qilish oynani yopadi', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ReportDialog postId={POST_ID} onClose={onClose} />);

    await user.click(screen.getByRole('button', { name: uz.detail.reportCancel }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
