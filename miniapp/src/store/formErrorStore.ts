import { create } from 'zustand';
import { STEP, type StepKey } from '../analytics/events';

/**
 * Serverdan kelgan maydon xatolari. Formadan alohida turadi, chunki bu
 * vaqtinchalik holat — draftga saqlanmaydi.
 */
interface FormErrorState {
  errors: Record<string, string>;
  setErrors: (errors: Record<string, string>) => void;
  clear: () => void;
}

export const useFormErrorStore = create<FormErrorState>((set) => ({
  errors: {},
  setErrors: (errors) => set({ errors }),
  clear: () => set({ errors: {} }),
}));

/** Qaysi maydon qaysi qadamga tegishli — xato bo'lsa o'sha qadamga qaytariladi. */
const FIELD_TO_STEP: Record<string, StepKey> = {
  postType: STEP.TYPE,
  direction: STEP.TYPE,
  originAirport: STEP.ROUTE,
  destAirport: STEP.ROUTE,
  originCityFree: STEP.ROUTE,
  destCityFree: STEP.ROUTE,
  departDate: STEP.ROUTE,
  deadlineDate: STEP.ROUTE,
  dateFlexibleDays: STEP.ROUTE,
  categoryIds: STEP.CARGO,
  weightKg: STEP.CARGO,
  weightKgMax: STEP.CARGO,
  priceAmount: STEP.CARGO,
  priceCurrency: STEP.CARGO,
  priceUnit: STEP.CARGO,
  comment: STEP.CARGO,
  contactPhone: STEP.CONTACT,
  contactTelegram: STEP.CONTACT,
  contactOther: STEP.CONTACT,
};

/** Birinchi xato qaysi qadamda — {@code null} bo'lsa xato preview ekranida. */
export function firstErrorStep(errors: Record<string, string>): StepKey | null {
  for (const field of Object.keys(errors)) {
    const step = FIELD_TO_STEP[field];
    if (step) {
      return step;
    }
  }
  return null;
}
