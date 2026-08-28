/**
 * Ko'rsatish formatlari (§11.3).
 *
 * <p>Barcha raqamlar mono shriftda va tik tekislanadi, shuning uchun
 * formatlash bitta joyda: sahifalar o'z-o'zicha `toFixed` yozmaydi.
 *
 * <p>Vaqt UTC'da saqlanadi va foydalanuvchi zonasida ko'rsatiladi (§5.1).
 */

const DATE_TIME = new Intl.DateTimeFormat('uz-UZ', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const DATE_ONLY = new Intl.DateTimeFormat('uz-UZ', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : DATE_TIME.format(date);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : DATE_ONLY.format(date);
}

/** Qisqa sana: grafik o'qlari uchun (05.08). */
export function formatShortDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return `${String(date.getDate()).padStart(2, '0')}.${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return new Intl.NumberFormat('uz-UZ').format(value);
}

/** 0.1234 -> "12.3%" */
export function formatPercent(ratio: number | null | undefined, digits = 1): string {
  if (ratio === null || ratio === undefined) {
    return '—';
  }
  return `${(ratio * 100).toFixed(digits)}%`;
}

export function formatMoney(
  amount: number | null | undefined,
  currency: string | null | undefined,
): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  return `${new Intl.NumberFormat('uz-UZ').format(amount)} ${currency ?? ''}`.trim();
}

/** Soniyani odam o'qiydigan ko'rinishga: 95 -> "1 daq 35 s". */
export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) {
    return '—';
  }
  if (seconds < 60) {
    return `${Math.round(seconds)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return rest === 0 ? `${minutes} daq` : `${minutes} daq ${rest} s`;
}

/** Ikki kunlik qiymatdan o'sish ulushi. */
export function delta(current: number, previous: number): number | null {
  if (!previous) {
    return null;
  }
  return (current - previous) / previous;
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Qoralama',
  PENDING: 'Kutmoqda',
  PUBLISHED: 'Chiqarilgan',
  REJECTED: 'Rad etilgan',
  EXPIRED: 'Muddati tugagan',
  CLOSED: 'Yopilgan',
  DELETED: "O'chirilgan",
  ACTIVE: 'Faol',
  LIMITED: 'Cheklangan',
  BLOCKED: 'Bloklangan',
  OPEN: 'Ochiq',
  REVIEWING: "Ko'rilmoqda",
  RESOLVED: 'Hal qilingan',
  DISMISSED: 'Rad etilgan',
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

const FUNNEL_LABELS: Record<string, string> = {
  form_open: 'Forma ochildi',
  step_1: '1-qadam',
  step_2: '2-qadam',
  step_3: '3-qadam',
  step_4: '4-qadam',
  preview: "Ko'rib chiqish",
  safety_ok: 'Xavfsizlik',
  published: 'Chiqarildi',
};

export function funnelLabel(stepKey: string): string {
  return FUNNEL_LABELS[stepKey] ?? stepKey;
}

const DAY_NAMES = ['Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sh', 'Ya'];

/** ISO hafta kuni (1 = dushanba). */
export function dayName(isoDayOfWeek: number): string {
  return DAY_NAMES[isoDayOfWeek - 1] ?? String(isoDayOfWeek);
}

/** Oxirgi N kun uchun sana oralig'i (ISO). */
export function lastDays(days: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}
