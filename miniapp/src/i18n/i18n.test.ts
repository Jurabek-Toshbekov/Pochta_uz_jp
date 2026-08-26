import { describe, expect, it } from 'vitest';
import { LANGUAGES, detectLanguage, dictionary } from './index';

/**
 * §16, 6-band: UI matnlari uch tilda bo'lishi shart. Kalit yetishmasa
 * ekranda `undefined` chiqadi — shu test buni oldini oladi.
 */
describe('i18n', () => {
  function keyPaths(value: unknown, prefix = ''): string[] {
    if (Array.isArray(value)) {
      return [prefix];
    }
    if (value && typeof value === 'object') {
      return Object.entries(value as Record<string, unknown>).flatMap(([key, nested]) =>
        keyPaths(nested, prefix ? `${prefix}.${key}` : key),
      );
    }
    return [prefix];
  }

  it('uch tilda ham kalitlar to‘liq mos', () => {
    const reference = keyPaths(dictionary('uz')).sort();

    for (const language of LANGUAGES) {
      expect(keyPaths(dictionary(language)).sort(), `til: ${language}`).toEqual(reference);
    }
  });

  it('hech bir matn bo‘sh emas', () => {
    for (const language of LANGUAGES) {
      const flat = JSON.stringify(dictionary(language));
      expect(flat, `til: ${language}`).not.toContain('""');
    }
  });

  it('taqiqlangan buyumlar ro‘yxati uch tilda ham bor (§7.3)', () => {
    for (const language of LANGUAGES) {
      expect(dictionary(language).preview.prohibitedList.length).toBeGreaterThanOrEqual(7);
    }
  });

  it('Telegram tilidan boshlang‘ich tanlov', () => {
    expect(detectLanguage('ru')).toBe('ru');
    expect(detectLanguage('ru-RU')).toBe('ru');
    expect(detectLanguage('uz')).toBe('uz');
    expect(detectLanguage(undefined)).toBe('uz');
    expect(detectLanguage('en')).toBe('uz');
  });
});
