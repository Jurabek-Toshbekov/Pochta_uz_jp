import type { ReactNode } from 'react';
import { haptic } from '../hooks/useTelegram';
import styles from './ui.module.css';

/** Label + hint + xato matni. Har bir input `aria-label` bilan (§9.5). */
export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.field}>
      <span className={styles.label}>{label}</span>
      {children}
      {hint && !error ? <span className={styles.hint}>{hint}</span> : null}
      {error ? (
        <span className={styles.error} role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}

export function Card({ children }: { children: ReactNode }) {
  return <div className={styles.card}>{children}</div>;
}

export interface SegmentOption<T extends string> {
  value: T;
  label: string;
}

export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: SegmentOption<T>[];
  value: T | null;
  onChange: (value: T) => void;
  ariaLabel: string;
}) {
  return (
    <div className={styles.segmented} role="radiogroup" aria-label={ariaLabel}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          className={`${styles.segment} ${value === option.value ? styles.segmentActive : ''}`}
          onClick={() => {
            haptic();
            onChange(option.value);
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function ChoiceCard({
  title,
  hint,
  active,
  onSelect,
}: {
  title: string;
  hint: string;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      className={`${styles.choice} ${active ? styles.choiceActive : ''}`}
      onClick={() => {
        haptic();
        onSelect();
      }}
    >
      <span className={styles.choiceTitle}>{title}</span>
      <span className={styles.choiceHint}>{hint}</span>
    </button>
  );
}

export function Chip({
  label,
  active,
  disabled = false,
  onToggle,
}: {
  label: string;
  active: boolean;
  /** Chegaraga yetilgan tanlov — bosilmaydi, lekin ko'rinib turadi. */
  disabled?: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      disabled={disabled}
      className={`${styles.chip} ${active ? styles.chipActive : ''}`}
      onClick={() => {
        haptic();
        onToggle();
      }}
    >
      {label}
    </button>
  );
}

export function ChipGroup({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className={styles.chips} role="group" aria-label={label}>
      {children}
    </div>
  );
}

/** Qadam indikatori: 4 ta chiziq + "2/4". */
export function StepProgress({ current, total }: { current: number; total: number }) {
  return (
    <div className={styles.progress} aria-label={`${current}/${total}`}>
      {Array.from({ length: total }, (_, index) => (
        <span
          key={index}
          className={`${styles.progressBar} ${index < current ? styles.progressBarDone : ''}`}
        />
      ))}
      <span className={styles.progressLabel}>
        {current}/{total}
      </span>
    </div>
  );
}

export function Notice({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <div className={styles.notice} role="note">
      {title ? <div className={styles.noticeTitle}>{title}</div> : null}
      {children}
    </div>
  );
}

export function CheckRow({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className={styles.check}>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => {
          haptic();
          onChange(event.target.checked);
        }}
      />
      <span>{label}</span>
    </label>
  );
}

export function Loader({ text }: { text: string }) {
  return (
    <div className={styles.state} role="status">
      {text}
    </div>
  );
}

/** Xato holati — har doim qayta urinish tugmasi bilan (§9.5). */
export function ErrorState({
  message,
  retryLabel,
  onRetry,
}: {
  message: string;
  retryLabel: string;
  onRetry?: () => void;
}) {
  return (
    <div className={styles.state} role="alert">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className={styles.button} onClick={onRetry}>
          {retryLabel}
        </button>
      ) : null}
    </div>
  );
}

export function EmptyState({
  message,
  actionLabel,
  onAction,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div className={styles.state}>
      <p>{message}</p>
      {actionLabel && onAction ? (
        <button type="button" className={styles.button} onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </div>
  );
}

export function GhostButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button type="button" className={styles.buttonGhost} onClick={onClick}>
      {label}
    </button>
  );
}

export function PrimaryButton({
  label,
  onClick,
  disabled,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button type="button" className={styles.button} onClick={onClick} disabled={disabled}>
      {label}
    </button>
  );
}

export const uiStyles = styles;
