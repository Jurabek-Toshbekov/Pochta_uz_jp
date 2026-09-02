import type { Airport } from '../../api/types';
import { usePostFormStore } from '../../store/postFormStore';
import { useT } from '../../i18n/useT';
import { AirportPicker } from '../../components/AirportPicker';
import { CheckRow, Field, SegmentedControl, uiStyles as styles } from '../../components/primitives';
import { STEP } from '../../analytics/events';
import { airportCity } from '../../lib/boardingPass';
import { useLanguage } from '../../store/appStore';

interface Props {
  originAirports: Airport[];
  destAirports: Airport[];
  fieldErrors: Record<string, string>;
}

/**
 * 2-qadam — yo'nalish va sana (§9.2).
 * Sana kalendardan olinadi (`input[type=date]`), erkin matn emas (§1.5).
 */
export function Step2Route({ originAirports, destAirports, fieldErrors }: Props) {
  const t = useT();
  const language = useLanguage();
  const state = usePostFormStore();
  const { patch } = state;

  const today = new Date().toISOString().slice(0, 10);
  const dateLabel = state.postType === 'CARRY' ? t.step2.dateCarry : t.step2.dateSend;

  const origin = endpointLabel(
    state.originAirport,
    state.originCityFree,
    originAirports,
    language,
    t.step2.pickOrigin,
  );
  const dest = endpointLabel(
    state.destAirport,
    state.destCityFree,
    destAirports,
    language,
    t.step2.pickDest,
  );

  return (
    <div className={`${styles.section} ${styles.slide}`}>
      <h2>{t.step2.title}</h2>

      {/*
        Tanlangan yo'nalish tepada turadi: ro'yxat uzun va foydalanuvchi
        pastga surganda nimani tanlaganini ko'rmay qoladi.
      */}
      <Field label={t.step2.selectedRoute}>
        <div className={styles.routeSummary}>
          <span className={styles.routeSummaryCode} data-empty={String(!origin.chosen)}>
            {origin.code}
            <span className={styles.routeSummaryCity}>{origin.city}</span>
          </span>
          <span className={styles.routeSummaryArrow} aria-hidden="true">
            &rarr;
          </span>
          <span className={styles.routeSummaryCode} data-empty={String(!dest.chosen)}>
            {dest.code}
            <span className={styles.routeSummaryCity}>{dest.city}</span>
          </span>
        </div>
      </Field>

      <AirportPicker
        label={t.step2.origin}
        airports={originAirports}
        selectedCode={state.originAirport}
        freeCity={state.originCityFree}
        onSelect={(code) => patch({ originAirport: code }, STEP.ROUTE)}
        onFreeCityChange={(value) => patch({ originCityFree: value }, STEP.ROUTE)}
        error={fieldErrors.originAirport}
      />

      <AirportPicker
        label={t.step2.dest}
        airports={destAirports}
        selectedCode={state.destAirport}
        freeCity={state.destCityFree}
        onSelect={(code) => patch({ destAirport: code }, STEP.ROUTE)}
        onFreeCityChange={(value) => patch({ destCityFree: value }, STEP.ROUTE)}
        error={fieldErrors.destAirport}
      />

      <Field label={`${t.step2.finalDestination} (${t.common.optional})`} hint={t.step2.finalDestinationHint}>
        <input
          type="text"
          value={state.finalDestination}
          aria-label={t.step2.finalDestination}
          maxLength={120}
          onChange={(event) => patch({ finalDestination: event.target.value }, STEP.ROUTE)}
        />
      </Field>

      <Field
        label={dateLabel}
        error={fieldErrors.departDate ?? fieldErrors.deadlineDate}
      >
        <input
          type="date"
          value={state.date}
          min={today}
          aria-label={dateLabel}
          className={fieldErrors.departDate || fieldErrors.deadlineDate ? styles.inputError : undefined}
          onChange={(event) => patch({ date: event.target.value }, STEP.ROUTE)}
        />
      </Field>

      <CheckRow
        label={t.step2.flexible}
        checked={state.dateFlexible}
        onChange={(value) => patch({ dateFlexible: value }, STEP.ROUTE)}
      />

      {state.dateFlexible ? (
        <SegmentedControl
          ariaLabel={t.step2.flexible}
          value={String(state.dateFlexibleDays)}
          onChange={(value) => patch({ dateFlexibleDays: Number(value) }, STEP.ROUTE)}
          options={[
            { value: '1', label: `±1 ${t.step2.flexibleDays}` },
            { value: '3', label: `±3 ${t.step2.flexibleDays}` },
            { value: '7', label: `±7 ${t.step2.flexibleDays}` },
          ]}
        />
      ) : null}
    </div>
  );
}

/** Xulosa satri uchun: kod va shahar nomi (tanlanmagan bo'lsa — taklif). */
function endpointLabel(
  code: string | null,
  freeCity: string,
  airports: Airport[],
  language: ReturnType<typeof useLanguage>,
  placeholder: string,
): { code: string; city: string; chosen: boolean } {
  if (code) {
    const airport = airports.find((candidate) => candidate.code === code);
    return {
      code,
      city: airportCity(airport, language) ?? '',
      chosen: true,
    };
  }
  if (freeCity.trim()) {
    return { code: '•••', city: freeCity.trim(), chosen: true };
  }
  return { code: '—', city: placeholder, chosen: false };
}
