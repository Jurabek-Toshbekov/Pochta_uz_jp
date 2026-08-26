import { create } from 'zustand';
import type {
  Currency,
  Direction,
  PostSearchParams,
  PostSort,
  PostType,
  SubscriptionInput,
} from '../api/types';

/**
 * Qidiruv filtrlari — UI holati (§14: Zustand faqat UI uchun).
 *
 * Natijalar TanStack Query'da; bu store faqat "foydalanuvchi nimani
 * tanladi" degan savolga javob beradi.
 */
export interface SearchFilters {
  type: PostType | null;
  direction: Direction | null;
  origin: string[];
  dest: string[];
  dateFrom: string;
  dateTo: string;
  categories: number[];
  priceMax: string;
  currency: Currency | null;
  verifiedOnly: boolean;
  q: string;
  sort: PostSort;
}

const EMPTY: SearchFilters = {
  type: null,
  direction: null,
  origin: [],
  dest: [],
  dateFrom: '',
  dateTo: '',
  categories: [],
  priceMax: '',
  currency: null,
  verifiedOnly: false,
  q: '',
  sort: 'NEWEST',
};

interface SearchState extends SearchFilters {
  patch: (values: Partial<SearchFilters>) => void;
  toggleIn: (key: 'origin' | 'dest', value: string) => void;
  toggleCategory: (id: number) => void;
  reset: () => void;
}

export const useSearchStore = create<SearchState>((set) => ({
  ...EMPTY,
  patch: (values) => set(values),
  toggleIn: (key, value) =>
    set((state) => ({
      [key]: state[key].includes(value)
        ? state[key].filter((item) => item !== value)
        : [...state[key], value],
    }) as Partial<SearchState>),
  toggleCategory: (id) =>
    set((state) => ({
      categories: state.categories.includes(id)
        ? state.categories.filter((item) => item !== id)
        : [...state.categories, id],
    })),
  reset: () => set({ ...EMPTY }),
}));

function toNumber(value: string): number | null {
  const trimmed = value.trim().replace(',', '.');
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

/** Filtrlarni API parametrlariga o'giradi. */
export function toSearchParams(filters: SearchFilters): PostSearchParams {
  return {
    type: filters.type,
    direction: filters.direction,
    origin: filters.origin,
    dest: filters.dest,
    dateFrom: filters.dateFrom || null,
    dateTo: filters.dateTo || null,
    categories: filters.categories,
    priceMax: toNumber(filters.priceMax),
    currency: filters.currency,
    verifiedOnly: filters.verifiedOnly,
    q: filters.q.trim() || null,
    sort: filters.sort,
  };
}

/**
 * Filtrlarni obunaga o'giradi (§10.3).
 *
 * Backend bitta aeroport kutadi — obuna aniq yo'nalish uchun bo'ladi.
 * Ko'p tanlov bo'lsa birinchisini olamiz: bu "hammasi" degan noaniq
 * obunadan ko'ra foydalilroq.
 */
export function toSubscriptionInput(filters: SearchFilters): SubscriptionInput {
  return {
    postType: filters.type,
    direction: filters.direction,
    originAirport: filters.origin[0] ?? null,
    destAirport: filters.dest[0] ?? null,
    dateFrom: filters.dateFrom || null,
    dateTo: filters.dateTo || null,
    categoryIds: filters.categories,
  };
}

/** Obuna uchun kamida bitta shart bo'lishi kerak — aks holda backend rad etadi. */
export function canSubscribe(filters: SearchFilters): boolean {
  const input = toSubscriptionInput(filters);
  return Boolean(
    input.postType ||
      input.direction ||
      input.originAirport ||
      input.destAirport ||
      input.dateFrom ||
      input.dateTo ||
      (input.categoryIds && input.categoryIds.length > 0),
  );
}

export function activeFilterCount(filters: SearchFilters): number {
  return [
    filters.type,
    filters.direction,
    filters.origin.length > 0 ? 'x' : '',
    filters.dest.length > 0 ? 'x' : '',
    filters.dateFrom,
    filters.dateTo,
    filters.categories.length > 0 ? 'x' : '',
    filters.priceMax,
    filters.verifiedOnly ? 'x' : '',
    filters.q.trim(),
  ].filter(Boolean).length;
}
