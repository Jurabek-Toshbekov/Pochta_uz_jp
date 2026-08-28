import { describe, expect, it } from 'vitest';
import {
  dayName,
  delta,
  formatDuration,
  formatMoney,
  formatNumber,
  formatPercent,
  formatShortDate,
  funnelLabel,
  lastDays,
  statusLabel,
  toIsoDate,
} from './format';

describe('formatPercent', () => {
  it('ulushni foizga aylantiradi', () => {
    expect(formatPercent(0.1234)).toBe('12.3%');
    expect(formatPercent(1)).toBe('100.0%');
  });

  it("qiymat yo'q bo'lsa chiziqcha", () => {
    expect(formatPercent(null)).toBe('—');
    expect(formatPercent(undefined)).toBe('—');
  });

  it('nolni nol deb ko\u2019rsatadi, chiziqcha emas', () => {
    expect(formatPercent(0)).toBe('0.0%');
  });
});

describe('formatDuration', () => {
  it('soniya, daqiqa va aralash ko\u2019rinish', () => {
    expect(formatDuration(45)).toBe('45 s');
    expect(formatDuration(120)).toBe('2 daq');
    expect(formatDuration(95)).toBe('1 daq 35 s');
  });

  it("nol yoki bo'sh qiymat", () => {
    expect(formatDuration(0)).toBe('—');
    expect(formatDuration(null)).toBe('—');
  });
});

describe('delta', () => {
  it("o'sish va pasayishni hisoblaydi", () => {
    expect(delta(12, 10)).toBeCloseTo(0.2);
    expect(delta(8, 10)).toBeCloseTo(-0.2);
  });

  it('avvalgi qiymat nol bo\u2019lsa hisoblamaydi', () => {
    expect(delta(5, 0)).toBeNull();
  });
});

describe('formatNumber va formatMoney', () => {
  it('raqamni formatlaydi', () => {
    expect(formatNumber(1000)).toContain('1');
    expect(formatNumber(null)).toBe('—');
  });

  it('valyutani qo\u2019shadi', () => {
    expect(formatMoney(2000, 'JPY')).toContain('JPY');
    expect(formatMoney(null, 'JPY')).toBe('—');
  });
});

describe('formatShortDate', () => {
  it('kun.oy ko\u2019rinishida', () => {
    expect(formatShortDate('2026-08-05')).toBe('05.08');
  });

  it("sana buzilgan bo'lsa kirish qiymatini qaytaradi", () => {
    expect(formatShortDate('yolgon')).toBe('yolgon');
  });
});

describe('yorliqlar', () => {
  it("holat o'zbekchaga o'giriladi", () => {
    expect(statusLabel('PENDING')).toBe('Kutmoqda');
    expect(statusLabel('NOMA_LUM')).toBe('NOMA_LUM');
  });

  it('voronka qadamlari nomlanadi', () => {
    expect(funnelLabel('form_open')).toBe('Forma ochildi');
    expect(funnelLabel('boshqa')).toBe('boshqa');
  });

  it('hafta kunlari', () => {
    expect(dayName(1)).toBe('Du');
    expect(dayName(7)).toBe('Ya');
  });
});

describe('lastDays', () => {
  it("oraliq ISO formatida va 'from' oldin turadi", () => {
    const range = lastDays(30);
    expect(range.from < range.to).toBe(true);
    expect(range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('toIsoDate', () => {
  it('mahalliy sanani ISO ga o\u2019giradi', () => {
    expect(toIsoDate(new Date(2026, 7, 5))).toBe('2026-08-05');
  });
});
