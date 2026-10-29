import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { PostFilters } from '../api/types';
import { Select } from './ui';

/**
 * Moderatsiya filtrlari paneli.
 *
 * <p>Holat sahifada emas, URL'da turadi (PostsPage) — shuning uchun filtrli
 * havolani boshqa moderatorga yuborish yoki profildan "barcha e'lonlari"ga
 * o'tish mumkin.
 */

export const DEFAULT_POST_FILTERS: PostFilters = {
  status: 'PENDING',
  type: '',
  direction: '',
  search: '',
  origin: '',
  dest: '',
  category: '',
  risk: '',
  reportedOnly: '',
  userId: '',
  from: '',
  to: '',
  sort: '',
};

const STATUS_OPTIONS = [
  { value: '', label: 'Barchasi' },
  { value: 'PENDING', label: 'Kutmoqda' },
  { value: 'PUBLISHED', label: 'Chiqarilgan' },
  { value: 'REJECTED', label: 'Rad etilgan' },
  { value: 'EXPIRED', label: 'Muddati o‘tgan' },
  { value: 'CLOSED', label: 'Yopilgan' },
];

const TYPE_OPTIONS = [
  { value: '', label: 'Hammasi' },
  { value: 'CARRY', label: 'Olib ketaman' },
  { value: 'SEND', label: 'Yubormoqchiman' },
];

const DIRECTION_OPTIONS = [
  { value: '', label: 'Hammasi' },
  { value: 'JP_UZ', label: 'JP → UZ' },
  { value: 'UZ_JP', label: 'UZ → JP' },
];

const RISK_OPTIONS = [
  { value: '', label: 'Hammasi' },
  { value: 'HIGH', label: 'Yuqori' },
  { value: 'MEDIUM', label: 'O‘rta' },
  { value: 'LOW', label: 'Past' },
];

const SORT_OPTIONS = [
  { value: '', label: 'Navbat (kutayotgan + risk)' },
  { value: 'NEWEST', label: 'Eng yangi' },
  { value: 'OLDEST', label: 'Eng eski' },
  { value: 'VIEWS', label: 'Ko‘p ko‘rilgan' },
  { value: 'REVEALS', label: 'Ko‘p kontakt ochilgan' },
  { value: 'REPORTS', label: 'Ko‘p shikoyat' },
  { value: 'TRAVEL_DATE', label: 'Uchish sanasi' },
];

export function PostFiltersBar({
  filters,
  onChange,
  onReset,
}: {
  filters: PostFilters;
  onChange: (patch: Partial<PostFilters>) => void;
  onReset: () => void;
}) {
  const reference = useQuery({ queryKey: ['admin-reference'], queryFn: api.reference, staleTime: 3_600_000 });

  const airportOptions = [
    { value: '', label: 'Hammasi' },
    ...(reference.data?.airports ?? []).map((airport) => ({
      value: airport.code,
      label: `${airport.code} · ${airport.cityUz}`,
    })),
  ];
  const categoryOptions = [
    { value: '', label: 'Hammasi' },
    ...(reference.data?.categories ?? []).map((category) => ({
      value: String(category.id),
      label: `${category.emoji ?? ''} ${category.titleUz}`.trim(),
    })),
  ];

  return (
    <>
      <div className="toolbar">
        <input
          placeholder="E‘lon havolasi/ID, username, ism yoki IATA"
          title="Kanal postini to‘liq qo‘yish ham mumkin — ID yoki havola bo‘lsa, boshqa filtrlar e’tiborga olinmaydi"
          value={filters.search}
          aria-label="Qidiruv"
          style={{ minWidth: 300 }}
          onChange={(event) => onChange({ search: event.target.value })}
        />
        <Select label="Holat" value={filters.status} options={STATUS_OPTIONS} onChange={(status) => onChange({ status })} />
        <Select label="Tur" value={filters.type} options={TYPE_OPTIONS} onChange={(type) => onChange({ type })} />
        <Select
          label="Yo‘nalish"
          value={filters.direction}
          options={DIRECTION_OPTIONS}
          onChange={(direction) => onChange({ direction })}
        />
        <Select label="Saralash" value={filters.sort} options={SORT_OPTIONS} onChange={(sort) => onChange({ sort })} />
      </div>
      <div className="toolbar">
        <Select label="Qayerdan" value={filters.origin} options={airportOptions} onChange={(origin) => onChange({ origin })} />
        <Select label="Qayerga" value={filters.dest} options={airportOptions} onChange={(dest) => onChange({ dest })} />
        <Select
          label="Kategoriya"
          value={filters.category}
          options={categoryOptions}
          onChange={(category) => onChange({ category })}
        />
        <Select label="Risk" value={filters.risk} options={RISK_OPTIONS} onChange={(risk) => onChange({ risk })} />
        <label className="muted" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
          Yaratilgan
          <input
            type="date"
            value={filters.from}
            aria-label="Yaratilgan sanadan"
            onChange={(event) => onChange({ from: event.target.value })}
          />
          —
          <input
            type="date"
            value={filters.to}
            aria-label="Yaratilgan sanagacha"
            onChange={(event) => onChange({ to: event.target.value })}
          />
        </label>
        <label className="muted" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
          <input
            type="checkbox"
            checked={filters.reportedOnly === 'true'}
            aria-label="Faqat shikoyat qilinganlar"
            onChange={(event) => onChange({ reportedOnly: event.target.checked ? 'true' : '' })}
          />
          Faqat shikoyat qilingan
        </label>
        {filters.userId && (
          <button type="button" onClick={() => onChange({ userId: '' })} title="Foydalanuvchi filtrini olib tashlash">
            Bitta foydalanuvchi ✕
          </button>
        )}
        <button type="button" onClick={onReset}>
          Tozalash
        </button>
      </div>
    </>
  );
}
