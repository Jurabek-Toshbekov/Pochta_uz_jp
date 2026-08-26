import type { LanguageCode } from '../api/types';
import { ru } from './ru';
import { uz, type Dictionary } from './uz';
import { uzCyrl } from './uz-cyrl';

const DICTIONARIES: Record<LanguageCode, Dictionary> = {
  uz,
  'uz-cyrl': uzCyrl,
  ru,
};

export const LANGUAGES: LanguageCode[] = ['uz', 'uz-cyrl', 'ru'];

export function dictionary(language: LanguageCode): Dictionary {
  return DICTIONARIES[language] ?? uz;
}

/** Telegram tilidan boshlang'ich tanlov (backend ham xuddi shunday qiladi). */
export function detectLanguage(telegramLanguageCode?: string): LanguageCode {
  if (telegramLanguageCode?.toLowerCase().startsWith('ru')) {
    return 'ru';
  }
  return 'uz';
}

export type { Dictionary };
