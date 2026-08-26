import type { Airport, CargoCategory, Currency, Direction, PostType } from '../api/types';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import { useT } from '../i18n/useT';
import { useSearchStore } from '../store/searchStore';
import { Chip, ChipGroup, Field, SegmentedControl, uiStyles as styles } from './primitives';

interface Props {
  airports: Airport[];
  categories: CargoCategory[];
}

/**
 * Filtr paneli (§10.1). Har bir o'zgarish {@code search_filter_change}
 * eventiga tushadi — qaysi filtrdan foydalanilmayotgani ko'rinib turadi.
 */
export function SearchFilters({ airports, categories }: Props) {
  const t = useT();
  const filters = useSearchStore();
  const { patch, toggleIn, toggleCategory } = filters;

  const change = (filter: string, value: unknown) => {
    track(EV.SEARCH_FILTER_CHANGE, { filter, value: String(value) });
  };

  const jpAirports = airports.filter((airport) => airport.countryCode === 'JP');
  const uzAirports = airports.filter((airport) => airport.countryCode === 'UZ');
  const originList = filters.direction === 'UZ_JP' ? uzAirports : jpAirports;
  const destList = filters.direction === 'UZ_JP' ? jpAirports : uzAirports;

  return (
    <div className={styles.section}>
      <Field label={t.search.title}>
        <input
          type="search"
          value={filters.q}
          aria-label={t.search.placeholder}
          placeholder={t.search.placeholder}
          onChange={(event) => patch({ q: event.target.value })}
          onBlur={() => filters.q && change('q', 'text')}
        />
      </Field>

      <Field label={t.step1.title}>
        <SegmentedControl
          ariaLabel={t.step1.title}
          value={filters.type ?? 'ANY'}
          onChange={(value) => {
            const next = value === 'ANY' ? null : (value as PostType);
            patch({ type: next });
            change('type', next);
          }}
          options={[
            { value: 'ANY', label: t.search.anyType },
            { value: 'CARRY', label: '✈️' },
            { value: 'SEND', label: '📦' },
          ]}
        />
      </Field>

      <Field label={t.step1.direction}>
        <SegmentedControl
          ariaLabel={t.step1.direction}
          value={filters.direction ?? 'ANY'}
          onChange={(value) => {
            const next = value === 'ANY' ? null : (value as Direction);
            // Yo'nalish o'zgarsa aeroport tanlovi ma'nosini yo'qotadi.
            patch({ direction: next, origin: [], dest: [] });
            change('direction', next);
          }}
          options={[
            { value: 'ANY', label: t.search.anyDirection },
            { value: 'JP_UZ', label: '🇯🇵 → 🇺🇿' },
            { value: 'UZ_JP', label: '🇺🇿 → 🇯🇵' },
          ]}
        />
      </Field>

      <Field label={t.step2.origin}>
        <ChipGroup label={t.step2.origin}>
          {originList.map((airport) => (
            <Chip
              key={airport.code}
              label={airport.code}
              active={filters.origin.includes(airport.code)}
              onToggle={() => {
                toggleIn('origin', airport.code);
                change('origin', airport.code);
              }}
            />
          ))}
        </ChipGroup>
      </Field>

      <Field label={t.step2.dest}>
        <ChipGroup label={t.step2.dest}>
          {destList.map((airport) => (
            <Chip
              key={airport.code}
              label={airport.code}
              active={filters.dest.includes(airport.code)}
              onToggle={() => {
                toggleIn('dest', airport.code);
                change('dest', airport.code);
              }}
            />
          ))}
        </ChipGroup>
      </Field>

      <Field label={`${t.step2.dateCarry} — ${t.step2.dateSend}`}>
        <div className={`${styles.row} ${styles.rowFill}`}>
          <input
            type="date"
            value={filters.dateFrom}
            aria-label={t.step2.dateCarry}
            onChange={(event) => {
              patch({ dateFrom: event.target.value });
              change('dateFrom', event.target.value);
            }}
          />
          <input
            type="date"
            value={filters.dateTo}
            aria-label={t.step2.dateSend}
            onChange={(event) => {
              patch({ dateTo: event.target.value });
              change('dateTo', event.target.value);
            }}
          />
        </div>
      </Field>

      <Field label={t.step3.categories}>
        <ChipGroup label={t.step3.categories}>
          {categories.map((category) => (
            <Chip
              key={category.id}
              label={`${category.emoji ?? ''} ${category.titleUz}`.trim()}
              active={filters.categories.includes(category.id)}
              onToggle={() => {
                toggleCategory(category.id);
                change('categories', category.code);
              }}
            />
          ))}
        </ChipGroup>
      </Field>

      <Field label={t.search.priceMax}>
        <div className={`${styles.row} ${styles.rowFill}`}>
          <input
            type="number"
            inputMode="decimal"
            min={1}
            value={filters.priceMax}
            aria-label={t.search.priceMax}
            onChange={(event) => patch({ priceMax: event.target.value })}
            onBlur={() => filters.priceMax && change('priceMax', filters.priceMax)}
          />
          <SegmentedControl
            ariaLabel={t.step3.currency}
            value={filters.currency ?? 'ANY'}
            onChange={(value) => {
              const next = value === 'ANY' ? null : (value as Currency);
              patch({ currency: next });
              change('currency', next);
            }}
            options={[
              { value: 'ANY', label: '—' },
              { value: 'JPY', label: '¥' },
              { value: 'USD', label: '$' },
              { value: 'UZS', label: "so'm" },
            ]}
          />
        </div>
      </Field>

      <ChipGroup label={t.search.verifiedOnly}>
        <Chip
          label={`✅ ${t.search.verifiedOnly}`}
          active={filters.verifiedOnly}
          onToggle={() => {
            patch({ verifiedOnly: !filters.verifiedOnly });
            change('verifiedOnly', !filters.verifiedOnly);
          }}
        />
      </ChipGroup>

      <Field label={t.search.sort} hint={filters.sort === 'CHEAPEST' ? t.search.cheapestNote : undefined}>
        <SegmentedControl
          ariaLabel={t.search.sort}
          value={filters.sort}
          onChange={(value) => {
            patch({ sort: value });
            change('sort', value);
          }}
          options={[
            { value: 'NEWEST', label: t.search.sortNewest },
            { value: 'DEPART_DATE', label: t.search.sortDate },
            { value: 'CHEAPEST', label: t.search.sortCheapest },
            { value: 'RATING', label: t.search.sortRating },
          ]}
        />
      </Field>
    </div>
  );
}
