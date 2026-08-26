import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import WebApp from '@twa-dev/sdk';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PreviewPage } from './PreviewPage';
import { usePostFormStore } from '../store/postFormStore';
import { uz } from '../i18n/uz';

/**
 * MUTLAQ QOIDA (§1.10, §7.3): 3 ta katakcha belgilanmaguncha "Kanalga
 * yuborish" tugmasi o'chiq. Bu test o'sha qoidani qo'riqlaydi — kimdir
 * checklist'ni "vaqtincha" olib tashlasa, test qizil bo'ladi.
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
    {
      code: 'TAS',
      countryCode: 'UZ',
      cityUz: 'Toshkent',
      cityRu: 'Ташкент',
      cityEn: 'Tashkent',
      nameEn: 'Tashkent',
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

function renderPreview() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/new/preview']}>
        <PreviewPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PreviewPage — xavfsizlik checklist', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    usePostFormStore.getState().reset();
    usePostFormStore.getState().patch({
      postType: 'CARRY',
      direction: 'JP_UZ',
      originAirport: 'NRT',
      destAirport: 'TAS',
      date: '2026-12-01',
      priceAmount: '2000',
      priceCurrency: 'JPY',
      priceUnit: 'PER_KG',
      categoryIds: [1],
      contactTelegram: 'testuser',
    });

    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/reference')) {
        return new Response(JSON.stringify(REFERENCE), { status: 200 });
      }
      return new Response('', { status: 204 });
    }) as unknown as typeof fetch;
  });

  it('boshida yuborish tugmasi o‘chiq', async () => {
    renderPreview();

    await waitFor(() => {
      expect(WebApp.MainButton.disable).toHaveBeenCalled();
    });
    expect(WebApp.MainButton.enable).not.toHaveBeenCalled();
  });

  it('uchta katakcha belgilangach tugma yonadi', async () => {
    const user = userEvent.setup();
    renderPreview();

    const boxes = await screen.findAllByRole('checkbox');
    expect(boxes).toHaveLength(3);

    await user.click(boxes[0]!);
    await user.click(boxes[1]!);
    expect(WebApp.MainButton.enable).not.toHaveBeenCalled();

    await user.click(boxes[2]!);
    await waitFor(() => {
      expect(WebApp.MainButton.enable).toHaveBeenCalled();
    });
  });

  it('taqiqlangan buyumlar ro‘yxati ochiladi (§7.3)', async () => {
    const user = userEvent.setup();
    renderPreview();

    const toggle = await screen.findByRole('button', { name: uz.preview.prohibitedTitle });
    await user.click(toggle);

    expect(screen.getByText(uz.preview.prohibitedList[0]!)).toBeInTheDocument();
  });

  it('e‘lon boarding pass ko‘rinishida ko‘rsatiladi', async () => {
    renderPreview();

    expect(await screen.findByText('NRT')).toBeInTheDocument();
    expect(screen.getByText('TAS')).toBeInTheDocument();
    expect(screen.getByText(uz.postType.CARRY)).toBeInTheDocument();
  });
});
