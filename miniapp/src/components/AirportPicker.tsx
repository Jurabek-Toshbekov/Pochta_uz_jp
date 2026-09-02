import { useMemo, useState } from 'react';
import type { Airport } from '../api/types';
import { useT } from '../i18n/useT';
import { haptic } from '../hooks/useTelegram';
import { uiStyles as styles } from './primitives';

interface Props {
  label: string;
  airports: Airport[];
  selectedCode: string | null;
  freeCity: string;
  onSelect: (code: string | null) => void;
  onFreeCityChange: (value: string) => void;
  error?: string;
}

/**
 * Aeroport tanlash: qidiruvli ro'yxat, mashhurlari tepada, "Boshqa" → erkin
 * matn (§9.2, 2-qadam).
 *
 * Erkin matn faqat ro'yxatda yo'q shahar uchun — tanlangan aeroport
 * doim IATA kodi bilan saqlanadi (§5.1).
 */
export function AirportPicker({
  label,
  airports,
  selectedCode,
  freeCity,
  onSelect,
  onFreeCityChange,
  error,
}: Props) {
  const t = useT();
  const [query, setQuery] = useState('');
  const [otherMode, setOtherMode] = useState(freeCity.length > 0);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return airports;
    }
    return airports.filter(
      (airport) =>
        airport.code.toLowerCase().includes(needle) ||
        airport.cityUz.toLowerCase().includes(needle) ||
        airport.cityRu.toLowerCase().includes(needle) ||
        airport.cityEn.toLowerCase().includes(needle) ||
        airport.nameEn.toLowerCase().includes(needle),
    );
  }, [airports, query]);

  const popular = filtered.filter((airport) => airport.popular);
  const rest = filtered.filter((airport) => !airport.popular);

  return (
    <div className={styles.field}>
      <span className={styles.label}>{label}</span>

      {otherMode ? (
        <>
          <input
            type="text"
            value={freeCity}
            aria-label={label}
            placeholder={t.step2.otherCityPlaceholder}
            className={error ? styles.inputError : undefined}
            onChange={(event) => onFreeCityChange(event.target.value)}
          />
          <button
            type="button"
            className={styles.buttonGhost}
            onClick={() => {
              setOtherMode(false);
              onFreeCityChange('');
            }}
          >
            {t.step2.searchAirport}
          </button>
        </>
      ) : (
        <>
          <input
            type="search"
            value={query}
            aria-label={t.step2.searchAirport}
            placeholder={t.step2.searchAirport}
            onChange={(event) => setQuery(event.target.value)}
          />

          <div className={styles.airportList}>
            {popular.length > 0 ? (
              <>
                <div className={styles.groupLabel}>{t.step2.popular}</div>
                {popular.map((airport) => (
                  <AirportRow
                    key={airport.code}
                    airport={airport}
                    active={selectedCode === airport.code}
                    onSelect={onSelect}
                  />
                ))}
              </>
            ) : null}

            {rest.map((airport) => (
              <AirportRow
                key={airport.code}
                airport={airport}
                active={selectedCode === airport.code}
                onSelect={onSelect}
              />
            ))}

            <button
              type="button"
              className={styles.airportItem}
              onClick={() => {
                haptic();
                onSelect(null);
                setOtherMode(true);
              }}
            >
              <span className={styles.airportCode}>+</span>
              <span className={styles.airportCity}>{t.step2.other}</span>
            </button>
          </div>
        </>
      )}

      {error ? (
        <span className={styles.error} role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}

function AirportRow({
  airport,
  active,
  onSelect,
}: {
  airport: Airport;
  active: boolean;
  onSelect: (code: string) => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      className={`${styles.airportItem} ${active ? styles.airportItemActive : ''}`}
      onClick={() => {
        haptic();
        onSelect(airport.code);
      }}
    >
      <span className={styles.airportCode}>{airport.code}</span>
      <span>
        <span className={styles.airportCity}>{airport.cityUz}</span>
        <br />
        <span className={styles.airportName}>{airport.nameEn}</span>
      </span>
    </button>
  );
}
