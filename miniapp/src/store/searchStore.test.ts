import { beforeEach, describe, expect, it } from 'vitest';
import {
  activeFilterCount,
  canSubscribe,
  toSearchParams,
  toSubscriptionInput,
  useSearchStore,
} from './searchStore';

describe('searchStore', () => {
  beforeEach(() => {
    useSearchStore.getState().reset();
  });

  it('bo‘sh filtrlarda hech narsa yuborilmaydi', () => {
    const params = toSearchParams(useSearchStore.getState());

    expect(params.dateFrom).toBeNull();
    expect(params.priceMax).toBeNull();
    expect(params.q).toBeNull();
    expect(params.sort).toBe('NEWEST');
  });

  it('narx matndan songa o‘giriladi, noto‘g‘ri qiymat null bo‘ladi', () => {
    useSearchStore.getState().patch({ priceMax: '2500' });
    expect(toSearchParams(useSearchStore.getState()).priceMax).toBe(2500);

    useSearchStore.getState().patch({ priceMax: 'arzon' });
    expect(toSearchParams(useSearchStore.getState()).priceMax).toBeNull();

    useSearchStore.getState().patch({ priceMax: '-5' });
    expect(toSearchParams(useSearchStore.getState()).priceMax).toBeNull();
  });

  it('aeroport tanlovi qo‘shiladi va olib tashlanadi', () => {
    const { toggleIn } = useSearchStore.getState();
    toggleIn('origin', 'NRT');
    toggleIn('origin', 'KIX');
    expect(useSearchStore.getState().origin).toEqual(['NRT', 'KIX']);

    toggleIn('origin', 'NRT');
    expect(useSearchStore.getState().origin).toEqual(['KIX']);
  });

  it('kategoriya tanlovi almashadi', () => {
    useSearchStore.getState().toggleCategory(1);
    useSearchStore.getState().toggleCategory(2);
    useSearchStore.getState().toggleCategory(1);

    expect(useSearchStore.getState().categories).toEqual([2]);
  });

  it('faol filtrlar soni hisoblanadi', () => {
    expect(activeFilterCount(useSearchStore.getState())).toBe(0);

    useSearchStore.getState().patch({ type: 'CARRY', q: '  noutbuk  ', verifiedOnly: true });
    useSearchStore.getState().toggleCategory(3);

    expect(activeFilterCount(useSearchStore.getState())).toBe(4);
  });

  it('sort filtrga sanalmaydi — u har doim tanlangan', () => {
    useSearchStore.getState().patch({ sort: 'CHEAPEST' });

    expect(activeFilterCount(useSearchStore.getState())).toBe(0);
  });

  describe('obunaga aylantirish (§10.3)', () => {
    it('shartsiz obuna bo‘lmaydi', () => {
      expect(canSubscribe(useSearchStore.getState())).toBe(false);
    });

    it('faqat matn qidiruvi obuna uchun yetarli emas', () => {
      // Backend matn bo'yicha obunani qo'llab-quvvatlamaydi — shu sabab
      // "chiqsa xabar bering" tugmasi ko'rsatilmaydi.
      useSearchStore.getState().patch({ q: 'noutbuk' });

      expect(canSubscribe(useSearchStore.getState())).toBe(false);
    });

    it('yo‘nalish tanlansa obuna bo‘ladi', () => {
      useSearchStore.getState().patch({ direction: 'JP_UZ' });

      expect(canSubscribe(useSearchStore.getState())).toBe(true);
    });

    it('ko‘p tanlovdan birinchi aeroport olinadi', () => {
      const { toggleIn } = useSearchStore.getState();
      toggleIn('origin', 'NRT');
      toggleIn('origin', 'KIX');
      toggleIn('dest', 'TAS');
      useSearchStore.getState().patch({ type: 'CARRY' });

      const input = toSubscriptionInput(useSearchStore.getState());

      expect(input.originAirport).toBe('NRT');
      expect(input.destAirport).toBe('TAS');
      expect(input.postType).toBe('CARRY');
    });

    it('bo‘sh sanalar null bo‘lib ketadi', () => {
      useSearchStore.getState().patch({ direction: 'UZ_JP' });
      const input = toSubscriptionInput(useSearchStore.getState());

      expect(input.dateFrom).toBeNull();
      expect(input.dateTo).toBeNull();
    });
  });
});
