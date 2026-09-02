import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import type { Currency, PostResponse, PriceUnit, UpdatePostRequest } from '../api/types';
import { ClosePostDialog } from '../components/ClosePostDialog';
import {
  Chip,
  ChipGroup,
  ErrorState,
  Field,
  GhostButton,
  Loader,
  Notice,
  SegmentedControl,
  uiStyles as styles,
} from '../components/primitives';
import { useMyPost, useUpdatePost } from '../hooks/usePostOwner';
import { categoryTitle, highRiskWarnings, useReference } from '../hooks/useReference';
import { hapticSuccess, useBackButton, useMainButton } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { useLanguage } from '../store/appStore';

const COMMENT_MAX = 1000;
const FLEXIBLE_DAYS = ['0', '1', '3', '7'] as const;

/** Tahrirlanadigan maydonlar — forma holati. Hammasi satr: input shunday beradi. */
interface EditState {
  date: string;
  dateFlexibleDays: string;
  finalDestination: string;
  weightKg: string;
  weightKgMax: string;
  priceAmount: string;
  priceCurrency: Currency;
  priceUnit: PriceUnit;
  categoryIds: number[];
  comment: string;
  contactTelegram: string;
  contactPhone: string;
  contactOther: string;
}

function toState(post: PostResponse): EditState {
  return {
    date: (post.postType === 'CARRY' ? post.departDate : post.deadlineDate) ?? '',
    dateFlexibleDays: String(post.dateFlexibleDays),
    finalDestination: post.finalDestination ?? '',
    weightKg: post.weightKg === null ? '' : String(post.weightKg),
    weightKgMax: post.weightKgMax === null ? '' : String(post.weightKgMax),
    priceAmount: post.priceAmount === null ? '' : String(post.priceAmount),
    priceCurrency: post.priceCurrency ?? 'JPY',
    priceUnit: post.priceUnit,
    categoryIds: [...post.categoryIds],
    comment: post.comment ?? '',
    contactTelegram: post.contactTelegram ?? '',
    contactPhone: post.contactPhone ?? '',
    contactOther: post.contactOther ?? '',
  };
}

function numberOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Tahrirlash so'rovi.
 *
 * Bo'sh satr ataylab `''` bo'lib ketadi, `null` emas: backendda bo'sh satr
 * "tozalang" degani, `undefined` esa "tegmang". Ikkalasini aralashtirsak
 * foydalanuvchi izohni o'chira olmasdi.
 */
function toRequest(state: EditState, postType: PostResponse['postType']): UpdatePostRequest {
  const negotiable = state.priceUnit === 'NEGOTIABLE';
  return {
    ...(postType === 'CARRY' ? { departDate: state.date } : { deadlineDate: state.date }),
    dateFlexibleDays: Number(state.dateFlexibleDays),
    finalDestination: state.finalDestination,
    weightKg: numberOrNull(state.weightKg),
    weightKgMax: numberOrNull(state.weightKgMax),
    priceUnit: state.priceUnit,
    ...(negotiable
      ? {}
      : { priceAmount: numberOrNull(state.priceAmount), priceCurrency: state.priceCurrency }),
    categoryIds: state.categoryIds,
    comment: state.comment,
    contactTelegram: state.contactTelegram,
    contactPhone: state.contactPhone,
    contactOther: state.contactOther,
  };
}

/**
 * `/my/:id/edit` — o'z e'lonini tahrirlash (§9.1).
 *
 * Tur, yo'nalish va aeroportlar ko'rsatiladi, lekin tahrirlanmaydi: ular
 * e'lonning o'zligi. Ularni almashtirish aslida yangi e'lon yasash bo'ladi,
 * ammo ko'rishlar, kontakt ochilishlari va kanaldagi post eskisiga tegishli
 * qolaveradi — natijada narx indeksi va talab/taklif metrikalari buziladi.
 */
export function EditPostPage() {
  const t = useT();
  const language = useLanguage();
  const navigate = useNavigate();
  const { postId = '' } = useParams<{ postId: string }>();

  const post = useMyPost(postId);
  const reference = useReference();
  const update = useUpdatePost(postId);

  const [state, setState] = useState<EditState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [closing, setClosing] = useState(false);

  useBackButton(useCallback(() => navigate('/my'), [navigate]));

  // Server javobi kelganda forma bir marta to'ldiriladi. Keyingi
  // yangilanishlar foydalanuvchi yozganini bosib ketmasligi kerak.
  useEffect(() => {
    if (post.data && state === null) {
      setState(toState(post.data));
    }
  }, [post.data, state]);

  const editable =
    post.data?.status === 'PUBLISHED' || post.data?.status === 'PENDING';

  const categories = reference.data?.categories ?? [];
  const warnings = useMemo(
    () => (state ? highRiskWarnings(categories, state.categoryIds) : []),
    [categories, state],
  );

  const patch = (change: Partial<EditState>) => {
    setMessage(null);
    setState((current) => (current ? { ...current, ...change } : current));
  };

  const toggleCategory = (id: number) => {
    setState((current) => {
      if (!current) {
        return current;
      }
      const has = current.categoryIds.includes(id);
      return {
        ...current,
        categoryIds: has
          ? current.categoryIds.filter((value) => value !== id)
          : [...current.categoryIds, id],
      };
    });
    setMessage(null);
  };

  const save = useCallback(async () => {
    if (!state || !post.data) {
      return;
    }
    setError(null);
    setFieldErrors({});
    try {
      await update.mutateAsync(toRequest(state, post.data.postType));
      hapticSuccess();
      setMessage(t.edit.saved);
    } catch (cause) {
      if (cause instanceof ApiError) {
        setFieldErrors(cause.fieldErrors);
        setError(Object.keys(cause.fieldErrors).length > 0 ? null : cause.message);
      } else {
        setError(t.common.errorNetwork);
      }
    }
  }, [post.data, state, t, update]);

  useMainButton({
    text: update.isPending ? t.edit.saving : t.edit.save,
    onClick: () => void save(),
    enabled: Boolean(state) && editable && !update.isPending,
    visible: Boolean(state) && editable,
    loading: update.isPending,
  });

  if (post.isLoading || !state) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (post.isError || !post.data) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void post.refetch()}
        />
      </div>
    );
  }

  const carry = post.data.postType === 'CARRY';
  const dateLabel = carry ? t.edit.dateCarry : t.edit.dateSend;
  const today = new Date().toISOString().slice(0, 10);
  const negotiable = state.priceUnit === 'NEGOTIABLE';

  if (!editable) {
    return (
      <div className="page">
        <h1>{t.edit.title}</h1>
        <Notice>{t.edit.notEditable}</Notice>
        <GhostButton label={t.my.title} onClick={() => navigate('/my')} />
      </div>
    );
  }

  return (
    <div className="page">
      <h1>{t.edit.title}</h1>
      <p className="muted">
        {t.postType[post.data.postType]} · {post.data.originAirport ?? post.data.originCityFree} →{' '}
        {post.data.destAirport ?? post.data.destCityFree}
      </p>
      <Notice>{t.edit.hint}</Notice>

      <Field label={dateLabel} error={fieldErrors.departDate ?? fieldErrors.deadlineDate}>
        <input
          type="date"
          value={state.date}
          min={today}
          aria-label={dateLabel}
          className={
            fieldErrors.departDate || fieldErrors.deadlineDate ? styles.inputError : undefined
          }
          onChange={(event) => patch({ date: event.target.value })}
        />
      </Field>

      <Field label={`${t.step2.flexible} (${t.step2.flexibleDays})`}>
        <SegmentedControl
          ariaLabel={t.step2.flexible}
          value={state.dateFlexibleDays}
          onChange={(value) => patch({ dateFlexibleDays: value })}
          options={FLEXIBLE_DAYS.map((days) => ({ value: days, label: `±${days}` }))}
        />
      </Field>

      <Field label={`${t.step2.finalDestination} (${t.common.optional})`}>
        <input
          type="text"
          value={state.finalDestination}
          maxLength={120}
          aria-label={t.step2.finalDestination}
          placeholder={t.step2.finalDestinationHint}
          onChange={(event) => patch({ finalDestination: event.target.value })}
        />
      </Field>

      <Field label={t.step3.categories} error={fieldErrors.categoryIds}>
        <ChipGroup label={t.step3.categories}>
          {categories.map((category) => (
            <Chip
              key={category.id}
              label={`${category.emoji ?? ''} ${categoryTitle(category, language)}`.trim()}
              active={state.categoryIds.includes(category.id)}
              onToggle={() => toggleCategory(category.id)}
            />
          ))}
        </ChipGroup>
      </Field>

      {warnings.map((category) => (
        <Notice
          key={category.id}
          title={`${t.step3.riskWarning}: ${categoryTitle(category, language)}`}
        >
          {category.warningUz}
        </Notice>
      ))}

      <Field
        label={`${t.step3.weight} (${t.common.kg})`}
        error={fieldErrors.weightKg ?? fieldErrors.weightKgMax}
      >
        <div className={`${styles.row} ${styles.rowFill}`}>
          <input
            type="number"
            inputMode="decimal"
            min={0.1}
            max={100}
            step={0.5}
            value={state.weightKg}
            aria-label={`${t.step3.weight} ${t.step3.weightFrom}`}
            placeholder={t.step3.weightFrom}
            onChange={(event) => patch({ weightKg: event.target.value })}
          />
          <input
            type="number"
            inputMode="decimal"
            min={0.1}
            max={100}
            step={0.5}
            value={state.weightKgMax}
            aria-label={`${t.step3.weight} ${t.step3.weightTo}`}
            placeholder={t.step3.weightTo}
            onChange={(event) => patch({ weightKgMax: event.target.value })}
          />
        </div>
      </Field>

      <Field label={t.step3.unit}>
        <SegmentedControl
          ariaLabel={t.step3.unit}
          value={state.priceUnit}
          onChange={(value) => patch({ priceUnit: value })}
          options={[
            { value: 'PER_KG', label: t.step3.unitPerKg },
            { value: 'TOTAL', label: t.step3.unitTotal },
            { value: 'NEGOTIABLE', label: t.step3.unitNegotiable },
          ]}
        />
      </Field>

      {negotiable ? null : (
        <Field label={t.step3.price} error={fieldErrors.priceAmount ?? fieldErrors.priceCurrency}>
          <div className={`${styles.row} ${styles.rowFill}`}>
            <input
              type="number"
              inputMode="decimal"
              min={1}
              value={state.priceAmount}
              aria-label={t.step3.price}
              className={fieldErrors.priceAmount ? styles.inputError : undefined}
              onChange={(event) => patch({ priceAmount: event.target.value })}
            />
            <SegmentedControl
              ariaLabel={t.step3.currency}
              value={state.priceCurrency}
              onChange={(value) => patch({ priceCurrency: value })}
              options={[
                { value: 'JPY', label: '¥' },
                { value: 'USD', label: '$' },
                { value: 'UZS', label: "so'm" },
              ]}
            />
          </div>
        </Field>
      )}

      <Field
        label={t.step3.comment}
        hint={`${state.comment.length}/${COMMENT_MAX}`}
        error={fieldErrors.comment}
      >
        <textarea
          rows={4}
          maxLength={COMMENT_MAX}
          value={state.comment}
          aria-label={t.step3.comment}
          placeholder={t.step3.commentPlaceholder}
          onChange={(event) => patch({ comment: event.target.value })}
        />
      </Field>

      <Field label={t.step4.telegram} error={fieldErrors.contactTelegram}>
        <input
          type="text"
          value={state.contactTelegram}
          maxLength={64}
          aria-label={t.step4.telegram}
          placeholder="@username"
          className={fieldErrors.contactTelegram ? styles.inputError : undefined}
          onChange={(event) => patch({ contactTelegram: event.target.value })}
        />
      </Field>

      <Field label={`${t.step4.phone} (${t.common.optional})`} error={fieldErrors.contactPhone}>
        <input
          type="tel"
          inputMode="tel"
          value={state.contactPhone}
          maxLength={32}
          aria-label={t.step4.phone}
          placeholder={t.step4.phonePlaceholder}
          onChange={(event) => patch({ contactPhone: event.target.value })}
        />
      </Field>

      <Field label={`${t.step4.other} (${t.common.optional})`} error={fieldErrors.contactOther}>
        <input
          type="text"
          value={state.contactOther}
          maxLength={160}
          aria-label={t.step4.other}
          placeholder={t.step4.otherPlaceholder}
          onChange={(event) => patch({ contactOther: event.target.value })}
        />
      </Field>

      {message ? <Notice>{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}

      {closing ? (
        <ClosePostDialog
          postId={postId}
          onClose={() => setClosing(false)}
          onClosed={() => navigate('/my')}
        />
      ) : (
        <GhostButton label={t.my.close} onClick={() => setClosing(true)} />
      )}
    </div>
  );
}
