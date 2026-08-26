import { create } from 'zustand';
import type { LanguageCode } from '../api/types';
import { detectLanguage } from '../i18n';

/**
 * UI holati (§14: TanStack Query — server holati, Zustand — faqat UI).
 *
 * Til tanlovi `localStorage`da saqlanadi: ilova qayta ochilganda darhol
 * to'g'ri tilda ko'rinishi kerak, server javobini kutmasdan.
 */
const LANGUAGE_KEY = 'pochta.language';

function readStoredLanguage(): LanguageCode | null {
  try {
    const value = localStorage.getItem(LANGUAGE_KEY);
    return value === 'uz' || value === 'uz-cyrl' || value === 'ru' ? value : null;
  } catch {
    return null;
  }
}

function persistLanguage(language: LanguageCode): void {
  try {
    localStorage.setItem(LANGUAGE_KEY, language);
  } catch {
    // Storage yo'q bo'lsa til faqat shu sessiyada saqlanadi — jim o'tamiz.
  }
}

interface AppState {
  language: LanguageCode;
  /** Deep link (`startapp=`) qiymati — marshrutga aylantirilgach tozalanadi. */
  startParam: string | null;
  setLanguage: (language: LanguageCode) => void;
  setStartParam: (value: string | null) => void;
}

export const useAppStore = create<AppState>((set) => ({
  language: readStoredLanguage() ?? detectLanguage(),
  startParam: null,
  setLanguage: (language) => {
    persistLanguage(language);
    set({ language });
  },
  setStartParam: (startParam) => set({ startParam }),
}));

/** Komponentlarda: `const t = useT();` */
export function useLanguage(): LanguageCode {
  return useAppStore((state) => state.language);
}
