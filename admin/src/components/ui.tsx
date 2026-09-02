import type { ReactNode } from 'react';
import { delta, formatPercent, statusLabel } from '../lib/format';

/**
 * Admin UI primitivlari (§11.3).
 *
 * <p>Bezak yo'q: hairline chiziqlar, mono raqamlar, uchta rang.
 * Har bir grafik ostida bitta jumlalik izoh bo'lishi shart —
 * shuning uchun {@link Card} `note` maydonini majburiy qilmaydi, lekin
 * grafik kartalarida u doim beriladi.
 */

export function Card({
  title,
  note,
  actions,
  children,
}: {
  title?: string;
  note?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="page-head" style={{ marginBottom: 12 }}>
          {title ? <h2>{title}</h2> : <span />}
          {actions}
        </div>
      )}
      {children}
      {note && <p className="card__note">{note}</p>}
    </section>
  );
}

/** KPI kartasi: katta mono raqam + o'zgarish ko'rsatkichi (§11.3). */
export function KpiCard({
  label,
  value,
  previous,
  current,
  hint,
}: {
  label: string;
  value: string;
  /** O'tgan davr qiymati — berilsa o'zgarish foizi chiziladi. */
  previous?: number;
  current?: number;
  hint?: string;
}) {
  const change =
    previous !== undefined && current !== undefined ? delta(current, previous) : null;

  return (
    <div className="card">
      <div className="kpi__label">{label}</div>
      <div className="kpi__value">{value}</div>
      {change !== null && (
        <div className={`kpi__delta ${change >= 0 ? 'kpi__delta--up' : 'kpi__delta--down'}`}>
          {change >= 0 ? '↑' : '↓'} {formatPercent(Math.abs(change))} — kechagiga nisbatan
        </div>
      )}
      {change === null && hint && <div className="kpi__delta muted">{hint}</div>}
    </div>
  );
}

const OK_STATUSES = new Set(['PUBLISHED', 'ACTIVE', 'RESOLVED']);
const WARN_STATUSES = new Set(['PENDING', 'REVIEWING', 'LIMITED', 'OPEN']);
const DANGER_STATUSES = new Set(['REJECTED', 'BLOCKED', 'DELETED']);

export function StatusBadge({ status }: { status: string }) {
  let modifier = '';
  if (OK_STATUSES.has(status)) {
    modifier = 'badge--ok';
  } else if (WARN_STATUSES.has(status)) {
    modifier = 'badge--warn';
  } else if (DANGER_STATUSES.has(status)) {
    modifier = 'badge--danger';
  }
  return <span className={`badge ${modifier}`}>{statusLabel(status)}</span>;
}

/** HIGH risk kategoriyasi moderatorga darhol ko'rinishi kerak (§7.3). */
export function RiskBadge({ level }: { level: string }) {
  if (level === 'HIGH') {
    return <span className="badge badge--danger">Yuqori risk</span>;
  }
  if (level === 'MEDIUM') {
    return <span className="badge badge--warn">O‘rta risk</span>;
  }
  return <span className="badge">Past</span>;
}

export function Loader({ text = 'Yuklanmoqda…' }: { text?: string }) {
  return <div className="empty">{text}</div>;
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <p className="error">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} style={{ marginTop: 12 }}>
          Qayta urinish
        </button>
      )}
    </div>
  );
}

export function EmptyState({ text }: { text: string }) {
  return <div className="empty">{text}</div>;
}

/** Sahifalash: offset — admin jadvali uchun yetarli. */
export function Pager({
  page,
  size,
  total,
  onChange,
}: {
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="toolbar" style={{ marginTop: 12, marginBottom: 0 }}>
      <button type="button" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        ← Oldingi
      </button>
      <span className="mono muted">
        {page + 1} / {pages}
      </span>
      <button type="button" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>
        Keyingi →
      </button>
      <span className="muted">Jami: <span className="mono">{total}</span></span>
    </div>
  );
}

/** Filtr uchun kichik select. */
export function Select({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="muted" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)} aria-label={label}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
