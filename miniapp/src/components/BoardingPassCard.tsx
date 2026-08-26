import type { ReactNode } from 'react';
import type { CargoCategory, Currency, PostType, PriceUnit } from '../api/types';
import { useT } from '../i18n/useT';
import styles from './BoardingPassCard.module.css';

export interface BoardingPassData {
  postType: PostType;
  originCode: string | null;
  destCode: string | null;
  originCityFree?: string | null;
  destCityFree?: string | null;
  originCity?: string | null;
  destCity?: string | null;
  finalDestination?: string | null;
  date: string | null;
  flexibleDays: number;
  weightKg?: number | null;
  weightKgMax?: number | null;
  priceAmount?: number | null;
  priceCurrency?: Currency | null;
  priceUnit: PriceUnit;
  categories: CargoCategory[];
  comment?: string | null;
}

interface Props {
  data: BoardingPassData;
  /** O'ng yuqoridagi kichik matn: status yoki sana. */
  status?: string;
  /** Publish muhri — faqat muvaffaqiyat ekranida (§9.4). */
  stamped?: boolean;
  footer?: ReactNode;
}

/**
 * E'lon — bu aslida chipta. Karta boarding pass ko'rinishida (§9.4).
 * Bu komponent forma ko'rib chiqishida, muvaffaqiyat ekranida va
 * "Mening e'lonlarim"da bir xil ishlatiladi.
 */
export function BoardingPassCard({ data, status, stamped = false, footer }: Props) {
  const t = useT();

  const dateText = formatDate(data.date);
  const flexText = data.flexibleDays > 0 ? ` ±${data.flexibleDays}` : '';

  return (
    <article className={styles.card}>
      <div className={styles.head}>
        <span className={styles.type}>{t.postType[data.postType]}</span>
        {status ? <span className={styles.status}>{status}</span> : null}
      </div>

      <div className={styles.route}>
        <Endpoint code={data.originCode} free={data.originCityFree} />
        <span className={styles.leg} aria-hidden="true">
          <span className={styles.legLine} />
          <span>✈</span>
          <span className={styles.legLine} />
        </span>
        <Endpoint code={data.destCode} free={data.destCityFree} />
      </div>

      {data.originCity && data.destCity ? (
        <p className={styles.cities}>
          {data.originCity} → {data.destCity}
          {data.finalDestination ? ` → ${data.finalDestination}` : ''}
        </p>
      ) : null}

      <div className={styles.grid}>
        <div className={styles.cell}>
          <div className={styles.label}>
            {data.postType === 'CARRY' ? t.step2.dateCarry : t.step2.dateSend}
          </div>
          <div className={styles.value}>
            {dateText}
            {flexText}
          </div>
        </div>

        <div className={styles.cell}>
          <div className={styles.label}>{t.step3.weight}</div>
          <div className={styles.value}>{formatWeight(data, t.common.kg)}</div>
        </div>

        <div className={styles.cell}>
          <div className={styles.label}>{t.step3.price}</div>
          <div className={styles.value}>
            {formatPrice(data, {
              PER_KG: t.step3.unitPerKg,
              TOTAL: t.step3.unitTotal,
              NEGOTIABLE: t.step3.unitNegotiable,
            })}
          </div>
        </div>
      </div>

      <div className={styles.perforation} aria-hidden="true" />

      <div className={styles.stub}>
        {data.categories.map((category) => (
          <span key={category.id} className={styles.tag}>
            {category.emoji ? `${category.emoji} ` : ''}
            {category.titleUz}
          </span>
        ))}
      </div>

      {data.comment ? <p className={styles.comment}>{data.comment}</p> : null}

      {footer}

      {stamped ? (
        <div className={styles.stamp} aria-hidden="true">
          印
          <br />
          OK
        </div>
      ) : null}
    </article>
  );
}

function Endpoint({ code, free }: { code: string | null; free?: string | null }) {
  if (code) {
    return <span className={styles.iata}>{code}</span>;
  }
  return <span className={styles.freeCity}>{free || '—'}</span>;
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—';
  }
  const parts = value.split('-');
  if (parts.length !== 3) {
    return value;
  }
  return `${parts[2]}.${parts[1]}.${parts[0]}`;
}

function formatWeight(data: BoardingPassData, kg: string): string {
  const max = data.weightKgMax ?? data.weightKg;
  if (max === null || max === undefined) {
    return '—';
  }
  return `${trimNumber(max)} ${kg}`;
}

function formatPrice(data: BoardingPassData, units: Record<PriceUnit, string>): string {
  if (data.priceUnit === 'NEGOTIABLE' || data.priceAmount === null || data.priceAmount === undefined) {
    return units.NEGOTIABLE;
  }
  return `${trimNumber(data.priceAmount)} ${data.priceCurrency ?? ''} / ${units[data.priceUnit]}`;
}

function trimNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : String(value);
}
