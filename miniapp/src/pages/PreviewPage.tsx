import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { EV } from '../analytics/events';
import { flush, track } from '../analytics/track';
import { BoardingPassCard, type BoardingPassData } from '../components/BoardingPassCard';
import {
  CheckRow,
  ErrorState,
  Notice,
  uiStyles as styles,
} from '../components/primitives';
import { useCreatePost } from '../hooks/useCreatePost';
import { useReference } from '../hooks/useReference';
import { useBackButton, useMainButton, hapticSuccess } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import {
  allChecksAccepted,
  toCreateRequest,
  usePostFormStore,
} from '../store/postFormStore';
import { firstErrorStep, useFormErrorStore } from '../store/formErrorStore';

/**
 * Ko'rib chiqish + xavfsizlik checklist'i (§9.2, §7.3).
 *
 * MUTLAQ QOIDA (§1.10): 3 ta katakcha belgilanmaguncha yuborish tugmasi
 * o'chiq turadi. Bu funksiya olib tashlanmaydi.
 */
export function PreviewPage() {
  const t = useT();
  const navigate = useNavigate();
  const reference = useReference();
  const createPost = useCreatePost();

  const form = usePostFormStore();
  const setCheck = usePostFormStore((state) => state.setCheck);
  const resetForm = usePostFormStore((state) => state.reset);
  const setErrors = useFormErrorStore((state) => state.setErrors);

  const [showProhibited, setShowProhibited] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const accepted = allChecksAccepted(form);

  useEffect(() => {
    track(EV.POST_PREVIEW_VIEW);
    track(EV.SAFETY_CHECKLIST_VIEW);
  }, []);

  const cardData = useMemo<BoardingPassData>(() => {
    const airports = reference.data?.airports ?? [];
    const categories = reference.data?.categories ?? [];
    const origin = airports.find((airport) => airport.code === form.originAirport);
    const dest = airports.find((airport) => airport.code === form.destAirport);

    return {
      postType: form.postType ?? 'CARRY',
      originCode: form.originAirport,
      destCode: form.destAirport,
      originCityFree: form.originCityFree,
      destCityFree: form.destCityFree,
      originCity: origin?.cityUz ?? null,
      destCity: dest?.cityUz ?? null,
      finalDestination: form.finalDestination,
      date: form.date || null,
      flexibleDays: form.dateFlexible ? form.dateFlexibleDays : 0,
      weightKg: form.weightKg ? Number(form.weightKg) : null,
      weightKgMax: form.weightKgMax ? Number(form.weightKgMax) : null,
      priceAmount: form.priceAmount ? Number(form.priceAmount) : null,
      priceCurrency: form.priceCurrency,
      priceUnit: form.priceUnit,
      categories: categories.filter((category) => form.categoryIds.includes(category.id)),
      comment: form.comment,
    };
  }, [form, reference.data]);

  const submit = useCallback(() => {
    if (!accepted || createPost.isPending) {
      return;
    }
    setSubmitError(null);
    track(EV.SAFETY_CHECKLIST_ACCEPT, { time_on_screen_ms: 0 });

    createPost.mutate(toCreateRequest(usePostFormStore.getState()), {
      onSuccess: (post) => {
        hapticSuccess();
        setErrors({});
        resetForm();
        void flush();
        navigate(`/success/${post.id}`, { state: { post } });
      },
      onError: (error) => {
        if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          setErrors(error.fieldErrors);
          Object.entries(error.fieldErrors).forEach(([field, message]) => {
            track(EV.POST_FORM_FIELD_ERROR, { field, error_code: message });
          });
          const step = firstErrorStep(error.fieldErrors);
          if (step) {
            navigate('/new', { state: { entryPoint: 'validation' } });
            return;
          }
        }
        setSubmitError(error.message);
      },
    });
  }, [accepted, createPost, navigate, resetForm, setErrors]);

  useMainButton({
    text: createPost.isPending ? t.preview.submitting : t.preview.submit,
    onClick: submit,
    enabled: accepted && !createPost.isPending,
    loading: createPost.isPending,
  });

  useBackButton(useCallback(() => navigate('/new'), [navigate]));

  return (
    <div className="page">
      <h1>{t.preview.title}</h1>
      <p className="muted">{t.preview.hint}</p>

      <BoardingPassCard data={cardData} />

      <div className={styles.card}>
        <h2>{t.preview.checklistTitle}</h2>
        <CheckRow label={t.preview.check1} checked={form.checks[0]} onChange={(v) => setCheck(0, v)} />
        <CheckRow label={t.preview.check2} checked={form.checks[1]} onChange={(v) => setCheck(1, v)} />
        <CheckRow label={t.preview.check3} checked={form.checks[2]} onChange={(v) => setCheck(2, v)} />

        <button
          type="button"
          className={styles.buttonGhost}
          aria-expanded={showProhibited}
          onClick={() => setShowProhibited((value) => !value)}
        >
          {t.preview.prohibitedTitle}
        </button>

        {showProhibited ? (
          <ul>
            {t.preview.prohibitedList.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        ) : null}
      </div>

      {!accepted ? <Notice>{t.preview.checklistTitle}: {t.preview.check2}</Notice> : null}

      {submitError ? (
        <ErrorState message={submitError} retryLabel={t.common.retry} onRetry={submit} />
      ) : null}
    </div>
  );
}
