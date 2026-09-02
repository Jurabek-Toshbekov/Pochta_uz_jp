import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BoardingPassCard, type BoardingPassData } from './BoardingPassCard';

/**
 * Og'irlik ko'rsatilishi (§9.4).
 *
 * Ilgari kartada faqat maksimal chiqardi va pastki chegara yo'qolardi:
 * "1 dan 50 kg gacha" e'loni "50 kg" bo'lib turardi. Bitta chegara
 * berilganda esa yo'nalish umuman bilinmasdi.
 */
const BASE: BoardingPassData = {
  postType: 'CARRY',
  originCode: 'NRT',
  destCode: 'TAS',
  originCityFree: null,
  destCityFree: null,
  originCity: 'Tokio',
  destCity: 'Toshkent',
  finalDestination: null,
  date: '2027-01-15',
  flexibleDays: 0,
  weightKg: null,
  weightKgMax: null,
  priceAmount: 2000,
  priceCurrency: 'JPY',
  priceUnit: 'PER_KG',
  categories: [],
  comment: null,
};

function renderWeight(weightKg: number | null, weightKgMax: number | null) {
  render(<BoardingPassCard data={{ ...BASE, weightKg, weightKgMax }} />);
}

describe('BoardingPassCard — og‘irlik', () => {
  it('ikkala chegara ham bo‘lsa oraliq chiqadi', () => {
    renderWeight(1, 50);

    expect(screen.getByText('1-50 kg')).toBeInTheDocument();
  });

  it('faqat maksimal bo‘lsa ≤ belgisi bilan', () => {
    renderWeight(null, 50);

    expect(screen.getByText('≤ 50 kg')).toBeInTheDocument();
  });

  it('faqat minimal bo‘lsa ≥ belgisi bilan', () => {
    renderWeight(5, null);

    expect(screen.getByText('≥ 5 kg')).toBeInTheDocument();
  });

  it('chegaralar teng bo‘lsa oraliq emas, aniq qiymat', () => {
    renderWeight(5, 5);

    expect(screen.getByText('5 kg')).toBeInTheDocument();
  });

  it('og‘irlik ko‘rsatilmagan bo‘lsa — chiziqcha', () => {
    renderWeight(null, null);

    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
