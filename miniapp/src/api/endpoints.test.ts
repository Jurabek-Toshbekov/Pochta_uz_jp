import { describe, expect, it } from 'vitest';
import { toSearchQuery } from './endpoints';

/** Query-string qurilishi — bo'sh filtr serverga yuborilmasligi kerak. */
describe('toSearchQuery', () => {
  it('bo‘sh filtrlarda query umuman bo‘lmaydi', () => {
    expect(toSearchQuery({})).toBe('');
    expect(toSearchQuery({ type: null, q: '', origin: [], verifiedOnly: false })).toBe('');
  });

  it('ro‘yxat vergul bilan birlashadi', () => {
    expect(toSearchQuery({ origin: ['NRT', 'KIX'] })).toBe('?origin=NRT%2CKIX');
    expect(toSearchQuery({ categories: [1, 2] })).toBe('?categories=1%2C2');
  });

  it('faqat true bo‘lgan toggle yuboriladi', () => {
    expect(toSearchQuery({ verifiedOnly: true })).toBe('?verifiedOnly=true');
    expect(toSearchQuery({ verifiedOnly: false })).toBe('');
  });

  it('0 qiymat tashlab yuborilmaydi', () => {
    expect(toSearchQuery({ size: 0 })).toBe('?size=0');
  });

  it('matn kodlanadi', () => {
    expect(toSearchQuery({ q: 'Farg‘ona shahri' })).toContain('q=Farg');
    expect(toSearchQuery({ q: 'a b' })).toBe('?q=a+b');
  });

  it('barcha filtrlar bir vaqtda', () => {
    const query = toSearchQuery({
      type: 'CARRY',
      direction: 'JP_UZ',
      origin: ['NRT'],
      dest: ['TAS'],
      dateFrom: '2026-09-01',
      dateTo: '2026-09-30',
      categories: [1],
      priceMax: 3000,
      currency: 'JPY',
      verifiedOnly: true,
      q: 'hujjat',
      sort: 'CHEAPEST',
      cursor: 'abc',
      size: 20,
    });

    expect(query).toContain('type=CARRY');
    expect(query).toContain('direction=JP_UZ');
    expect(query).toContain('dateFrom=2026-09-01');
    expect(query).toContain('priceMax=3000');
    expect(query).toContain('currency=JPY');
    expect(query).toContain('sort=CHEAPEST');
    expect(query).toContain('cursor=abc');
  });
});
