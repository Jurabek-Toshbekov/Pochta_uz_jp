import { create } from 'zustand';
import type { CreatePostRequest, Currency, Direction, PostType, PriceUnit } from '../api/types';
import { getPlatform, getSessionId } from '../analytics/track';

/**
 * 4 qadamli formaning holati (§9.2).
 *
 * Raqamlar `string` sifatida turadi — input'ning tabiiy holati shunday.
 * Serverga yuborishdan oldin {@link toCreateRequest} ularni tipli qiymatga
 * o'giradi: sana `LocalDate`, narx `BigDecimal` (§1.5).
 */
/**
 * Backenddagi `@Size(max = 5)` va `@DecimalMin/@DecimalMax` bilan bir xil
 * chegaralar. Bu yerda takrorlanishining sababi: chegara faqat serverda
 * bo'lsa, foydalanuvchi butun formani to'ldirib, oxirida 400 oladi.
 * Server baribir oxirgi hakam — bu faqat oldini olish.
 */
export const MAX_CATEGORIES = 5;
export const MIN_WEIGHT_KG = 0.1;
export const MAX_WEIGHT_KG = 100;

export interface PostFormState {
  postType: PostType | null;
  direction: Direction | null;

  originAirport: string | null;
  originCityFree: string;
  destAirport: string | null;
  destCityFree: string;
  finalDestination: string;

  /** ISO `yyyy-MM-dd`. CARRY uchun uchish sanasi, SEND uchun oxirgi muddat. */
  date: string;
  dateFlexible: boolean;
  dateFlexibleDays: number;

  weightKg: string;
  weightKgMax: string;

  priceAmount: string;
  priceCurrency: Currency;
  priceUnit: PriceUnit;

  categoryIds: number[];
  comment: string;

  contactPhone: string;
  contactTelegram: string;
  contactOther: string;

  /** §7.3 — uchtasi ham belgilanmaguncha yuborish tugmasi o'chiq. */
  checks: [boolean, boolean, boolean];

  formStartedAtMs: number | null;
  editCounts: Record<string, number>;
}

const EMPTY: PostFormState = {
  postType: null,
  direction: null,
  originAirport: null,
  originCityFree: '',
  destAirport: null,
  destCityFree: '',
  finalDestination: '',
  date: '',
  dateFlexible: false,
  dateFlexibleDays: 3,
  weightKg: '',
  weightKgMax: '',
  priceAmount: '',
  priceCurrency: 'JPY',
  priceUnit: 'PER_KG',
  categoryIds: [],
  comment: '',
  contactPhone: '',
  contactTelegram: '',
  contactOther: '',
  checks: [false, false, false],
  formStartedAtMs: null,
  editCounts: {},
};

interface PostFormActions {
  patch: (values: Partial<PostFormState>, step?: string) => void;
  toggleCategory: (id: number) => void;
  setCheck: (index: 0 | 1 | 2, value: boolean) => void;
  startForm: () => void;
  hydrate: (payload: Record<string, unknown>) => void;
  reset: () => void;
}

export const usePostFormStore = create<PostFormState & PostFormActions>((set, get) => ({
  ...EMPTY,

  patch: (values, step) =>
    set((state) => ({
      ...state,
      ...values,
      editCounts: step
        ? { ...state.editCounts, [step]: (state.editCounts[step] ?? 0) + 1 }
        : state.editCounts,
    })),

  toggleCategory: (id) =>
    set((state) => {
      if (state.categoryIds.includes(id)) {
        return { categoryIds: state.categoryIds.filter((value) => value !== id) };
      }
      // 6-tanlov jim yutiladi: chegarani ekran o'zi ko'rsatib turadi.
      if (state.categoryIds.length >= MAX_CATEGORIES) {
        return state;
      }
      return { categoryIds: [...state.categoryIds, id] };
    }),

  setCheck: (index, value) =>
    set((state) => {
      const checks: [boolean, boolean, boolean] = [...state.checks];
      checks[index] = value;
      return { checks };
    }),

  startForm: () => {
    if (get().formStartedAtMs === null) {
      set({ formStartedAtMs: Date.now() });
    }
  },

  hydrate: (payload) =>
    set((state) => {
      const next = { ...state, ...(payload as Partial<PostFormState>) };
      return {
        ...next,
        // Eski draftda chegaradan ortiq kategoriya bo'lishi mumkin (chegara
        // keyinroq qo'shilgan). Uni shu yerda kesmasak, draft tiklanishi
        // bilan forma yana serverdan 400 oladigan holatga qaytadi.
        categoryIds: Array.isArray(next.categoryIds)
          ? next.categoryIds.slice(0, MAX_CATEGORIES)
          : [],
        // Checklist hech qachon draftdan tiklanmaydi — har safar qaytadan
        // belgilanishi kerak (§7.3).
        checks: [false, false, false],
      };
    }),

  reset: () => set({ ...EMPTY, editCounts: {} }),
}));

/** Draftga saqlanadigan qism: checklist va vaqt belgilari saqlanmaydi. */
export function toDraftPayload(state: PostFormState): Record<string, unknown> {
  const { checks: _checks, editCounts: _editCounts, formStartedAtMs: _started, ...rest } = state;
  return rest as unknown as Record<string, unknown>;
}

export function allChecksAccepted(state: PostFormState): boolean {
  return state.checks.every(Boolean);
}

function toNumber(value: string): number | null {
  const trimmed = value.trim().replace(',', '.');
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Formani serverga yuboriladigan tipli DTO'ga o'giradi. */
export function toCreateRequest(state: PostFormState): CreatePostRequest {
  const isCarry = state.postType === 'CARRY';
  const negotiable = state.priceUnit === 'NEGOTIABLE';

  return {
    postType: state.postType as PostType,
    direction: state.direction as Direction,
    originAirport: state.originAirport,
    destAirport: state.destAirport,
    originCityFree: state.originAirport ? null : state.originCityFree.trim() || null,
    destCityFree: state.destAirport ? null : state.destCityFree.trim() || null,
    finalDestination: state.finalDestination.trim() || null,
    departDate: isCarry ? state.date || null : null,
    deadlineDate: isCarry ? null : state.date || null,
    dateFlexibleDays: state.dateFlexible ? state.dateFlexibleDays : 0,
    weightKg: toNumber(state.weightKg),
    weightKgMax: toNumber(state.weightKgMax),
    priceAmount: negotiable ? null : toNumber(state.priceAmount),
    priceCurrency: negotiable ? null : state.priceCurrency,
    priceUnit: state.priceUnit,
    categoryIds: state.categoryIds,
    comment: state.comment.trim() || null,
    contactPhone: state.contactPhone.trim() || null,
    contactTelegram: state.contactTelegram.trim().replace('@', '') || null,
    contactOther: state.contactOther.trim() || null,
    safetyChecklistOk: allChecksAccepted(state),
    sessionId: getSessionId(),
    platform: getPlatform(),
    formStartedAtMs: state.formStartedAtMs ?? undefined,
  };
}

/**
 * Og'irlik maydonlaridagi xato (bo'lmasa {@code null}).
 *
 * Og'irlik ixtiyoriy, lekin yozilgan bo'lsa chegaraga tushishi kerak —
 * aks holda xato faqat "Kanalga yuborish"da, ya'ni eng kech paytda chiqadi.
 */
export function weightError(state: PostFormState): 'range' | 'order' | null {
  const min = toNumber(state.weightKg);
  const max = toNumber(state.weightKgMax);
  const outOfRange = (value: number | null) =>
    value !== null && (value < MIN_WEIGHT_KG || value > MAX_WEIGHT_KG);

  if (outOfRange(min) || outOfRange(max)) {
    return 'range';
  }
  if (min !== null && max !== null && max < min) {
    return 'order';
  }
  return null;
}

/** Har bir qadam to'ldirilganini tekshiradi — "Davom etish" tugmasi shunga qarab yonadi. */
export function isStepComplete(step: string, state: PostFormState): boolean {
  switch (step) {
    case 'step1_type':
      return state.postType !== null && state.direction !== null;
    case 'step2_route':
      return (
        (state.originAirport !== null || state.originCityFree.trim().length > 1) &&
        (state.destAirport !== null || state.destCityFree.trim().length > 1) &&
        state.date.length === 10
      );
    case 'step3_cargo':
      return (
        state.categoryIds.length > 0 &&
        state.categoryIds.length <= MAX_CATEGORIES &&
        weightError(state) === null &&
        (state.priceUnit === 'NEGOTIABLE' || (toNumber(state.priceAmount) ?? 0) > 0)
      );
    case 'step4_contact':
      // Telegram ham, telefon ham majburiy: e'lon egasiga aynan shu ikki
      // yo'l bilan bog'lanishadi. Bittasi bo'lsa yarim kontakt bo'ladi va
      // "Bog'lanish" bosgan odam ko'pincha javob ololmaydi.
      return (
        state.contactTelegram.trim().length > 0 && state.contactPhone.trim().length > 0
      );
    default:
      return false;
  }
}
