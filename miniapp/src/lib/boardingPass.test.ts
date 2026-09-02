import { describe, expect, it } from 'vitest';
import type { Airport, CargoCategory } from '../api/types';
import { airportCity, isRedundantFinalDestination, toBoardingPassData } from './boardingPass';

const BHK: Airport = {
  code: 'BHK',
  countryCode: 'UZ',
  cityUz: 'Buxoro',
  cityRu: 'Бухара',
  cityEn: 'Bukhara',
  nameEn: 'Bukhara International Airport',
  latitude: null,
  longitude: null,
  popular: false,
  sortOrder: 30,
};

const FUK: Airport = {
  code: 'FUK',
  countryCode: 'JP',
  cityUz: 'Fukuoka',
  cityRu: 'Фукуока',
  cityEn: 'Fukuoka',
  nameEn: 'Fukuoka Airport',
  latitude: null,
  longitude: null,
  popular: false,
  sortOrder: 50,
};

const DOCUMENTS: CargoCategory = {
  id: 1,
  code: 'DOCUMENTS',
  titleUz: 'Hujjatlar',
  titleRu: 'Документы',
  emoji: '📄',
  riskLevel: 'LOW',
  warningUz: null,
  sortOrder: 10,
};

const POST = {
  postType: 'CARRY' as const,
  direction: 'JP_UZ' as const,
  originAirport: 'FUK',
  destAirport: 'BHK',
  originCityFree: null,
  destCityFree: null,
  finalDestination: 'Buxoro',
  departDate: '2026-12-01',
  deadlineDate: null,
  dateFlexibleDays: 0,
  weightKg: 2,
  weightKgMax: 6,
  priceAmount: 350000,
  priceCurrency: 'UZS' as const,
  priceUnit: 'TOTAL' as const,
  categoryIds: [1],
  comment: 'Kichik paket',
};

describe('isRedundantFinalDestination', () => {
  it('kelish shahrining o‘zi bo‘lsa ortiqcha', () => {
    expect(isRedundantFinalDestination('Buxoro', BHK, null)).toBe(true);
  });

  it('boshqa yozuvda yozilsa ham ortiqcha — uchala nom tekshiriladi', () => {
    // Foydalanuvchi rus tilida yozadi, ekran o'zbekcha ko'rsatadi.
    expect(isRedundantFinalDestination('Бухара', BHK, null)).toBe(true);
    expect(isRedundantFinalDestination('Bukhara', BHK, null)).toBe(true);
  });

  it('katta-kichik harf va bo‘shliq ahamiyatsiz', () => {
    expect(isRedundantFinalDestination('  buxoro ', BHK, null)).toBe(true);
  });

  it('haqiqiy yakuniy manzil ortiqcha emas', () => {
    expect(isRedundantFinalDestination('Gijduvon', BHK, null)).toBe(false);
  });

  it('erkin kiritilgan shahar bilan ham solishtiriladi', () => {
    expect(isRedundantFinalDestination('Angren', undefined, 'Angren')).toBe(true);
    expect(isRedundantFinalDestination('Angren', undefined, 'Toshkent')).toBe(false);
  });

  it('bo‘sh qiymat ortiqcha hisoblanadi', () => {
    expect(isRedundantFinalDestination(null, BHK, null)).toBe(true);
    expect(isRedundantFinalDestination('   ', BHK, null)).toBe(true);
  });
});

describe('airportCity', () => {
  it('til bo‘yicha nom qaytaradi, uz-cyrl lotinga qaytadi', () => {
    expect(airportCity(BHK, 'uz')).toBe('Buxoro');
    expect(airportCity(BHK, 'ru')).toBe('Бухара');
    expect(airportCity(BHK, 'uz-cyrl')).toBe('Buxoro');
    expect(airportCity(undefined, 'uz')).toBeNull();
  });
});

describe('toBoardingPassData', () => {
  it('shahar nomlari tilga ergashadi', () => {
    const ru = toBoardingPassData(POST, [FUK, BHK], [DOCUMENTS], 'ru');

    expect(ru.originCity).toBe('Фукуока');
    expect(ru.destCity).toBe('Бухара');
  });

  it('takroriy yakuniy manzil tashlab yuboriladi — til qanday bo‘lsa ham', () => {
    expect(toBoardingPassData(POST, [FUK, BHK], [DOCUMENTS], 'uz').finalDestination).toBeNull();
    expect(toBoardingPassData(POST, [FUK, BHK], [DOCUMENTS], 'ru').finalDestination).toBeNull();
  });

  it('haqiqiy yakuniy manzil saqlanadi', () => {
    const data = toBoardingPassData(
      { ...POST, finalDestination: 'Gijduvon' },
      [FUK, BHK],
      [DOCUMENTS],
      'uz',
    );

    expect(data.finalDestination).toBe('Gijduvon');
  });

  it('faqat tanlangan kategoriyalar to‘liq obyekt sifatida uzatiladi', () => {
    const data = toBoardingPassData(POST, [FUK, BHK], [DOCUMENTS], 'ru');

    expect(data.categories).toHaveLength(1);
    // Karta nomni o'zi tanlaydi, shuning uchun ikkala til ham kelishi kerak.
    expect(data.categories[0]?.titleRu).toBe('Документы');
    expect(data.categories[0]?.titleUz).toBe('Hujjatlar');
  });

  it('reference hali yuklanmagan bo‘lsa yiqilmaydi', () => {
    const data = toBoardingPassData(POST, undefined, undefined, 'uz');

    expect(data.originCity).toBeNull();
    expect(data.categories).toEqual([]);
  });
});
