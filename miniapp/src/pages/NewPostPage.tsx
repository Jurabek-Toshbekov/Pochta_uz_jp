import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { EV, STEP, STEP_ORDER, type StepKey } from '../analytics/events';
import { track } from '../analytics/track';
import { ErrorState, Loader, StepProgress, uiStyles as styles } from '../components/primitives';
import { airportsFor, useReference } from '../hooks/useReference';
import { useDraftAutosave, useDraftRestore } from '../hooks/useDraftSync';
import { useBackButton, useMainButton, haptic } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { isStepComplete, usePostFormStore } from '../store/postFormStore';
import { useFormErrorStore } from '../store/formErrorStore';
import { Step1Type } from './steps/Step1Type';
import { Step2Route } from './steps/Step2Route';
import { Step3Cargo } from './steps/Step3Cargo';
import { Step4Contact } from './steps/Step4Contact';

/**
 * E'lon berish — 4 qadam (§9.2). Bot'dagi 9 qadam shu ekranga sig'di.
 *
 * Voronka event'lari to'liq ulangan (§6.1): har bir qadam ko'rinishi,
 * tugallanishi, orqaga qaytishi va tashlab ketilishi yoziladi.
 */
export function NewPostPage() {
  const t = useT();
  const navigate = useNavigate();
  const location = useLocation();

  const form = usePostFormStore();
  const startForm = usePostFormStore((state) => state.startForm);
  const errors = useFormErrorStore((state) => state.errors);

  const reference = useReference();
  useDraftRestore(true);

  const [stepIndex, setStepIndex] = useState(0);
  const step = STEP_ORDER[stepIndex] as StepKey;

  const stepEnteredAt = useRef<number>(Date.now());
  const submitted = useRef(false);

  useDraftAutosave(step, true);

  // Forma ochilishi — voronkaning boshi.
  useEffect(() => {
    startForm();
    const entryPoint = (location.state as { entryPoint?: string } | null)?.entryPoint ?? 'home';
    track(EV.POST_FORM_OPEN, { entry_point: entryPoint });
  }, [startForm, location.state]);

  // Har bir qadam ko'rinishi.
  useEffect(() => {
    stepEnteredAt.current = Date.now();
    track(EV.POST_FORM_STEP_VIEW, { step, step_index: stepIndex + 1 });
  }, [step, stepIndex]);

  // Tashlab ketish — publish bo'lmagan holatda chiqib ketilsa.
  useEffect(
    () => () => {
      if (submitted.current) {
        return;
      }
      track(EV.POST_FORM_ABANDON, {
        last_step: step,
        time_total_ms: form.formStartedAtMs ? Date.now() - form.formStartedAtMs : 0,
        filled_fields: filledFieldCount(),
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  function filledFieldCount(): number {
    const state = usePostFormStore.getState();
    return [
      state.postType,
      state.direction,
      state.originAirport ?? state.originCityFree,
      state.destAirport ?? state.destCityFree,
      state.date,
      state.categoryIds.length > 0 ? 'yes' : '',
      state.priceAmount || state.priceUnit === 'NEGOTIABLE' ? 'yes' : '',
      state.contactTelegram || state.contactPhone || state.contactOther,
    ].filter(Boolean).length;
  }

  const complete = isStepComplete(step, form);

  const goNext = useCallback(() => {
    if (!complete) {
      return;
    }
    haptic();
    track(EV.POST_FORM_STEP_COMPLETE, {
      step,
      time_on_step_ms: Date.now() - stepEnteredAt.current,
      edit_count: form.editCounts[step] ?? 0,
    });

    if (stepIndex < STEP_ORDER.length - 1) {
      setStepIndex(stepIndex + 1);
    } else {
      submitted.current = true;
      navigate('/new/preview');
    }
  }, [complete, step, stepIndex, form.editCounts, navigate]);

  const goBack = useCallback(() => {
    haptic();
    if (stepIndex === 0) {
      submitted.current = true;
      navigate('/');
      return;
    }
    track(EV.POST_FORM_STEP_BACK, { step: STEP_ORDER[stepIndex - 1], from_step: step });
    setStepIndex(stepIndex - 1);
  }, [stepIndex, step, navigate]);

  useMainButton({ text: t.common.next, onClick: goNext, enabled: complete });
  useBackButton(goBack);

  if (reference.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (reference.isError || !reference.data) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void reference.refetch()}
        />
      </div>
    );
  }

  const originAirports = airportsFor(reference.data.airports, form.direction, 'origin');
  const destAirports = airportsFor(reference.data.airports, form.direction, 'dest');

  return (
    <div className="page">
      <StepProgress current={stepIndex + 1} total={STEP_ORDER.length} />

      {step === STEP.TYPE ? <Step1Type /> : null}
      {step === STEP.ROUTE ? (
        <Step2Route
          originAirports={originAirports}
          destAirports={destAirports}
          fieldErrors={errors}
        />
      ) : null}
      {step === STEP.CARGO ? (
        <Step3Cargo categories={reference.data.categories} fieldErrors={errors} />
      ) : null}
      {step === STEP.CONTACT ? <Step4Contact fieldErrors={errors} /> : null}

      {!complete ? <p className={styles.hint}>{hintFor(step, t)}</p> : null}
    </div>
  );
}

function hintFor(step: StepKey, t: ReturnType<typeof useT>): string {
  switch (step) {
    case STEP.TYPE:
      return t.step1.title;
    case STEP.ROUTE:
      return t.step2.title;
    case STEP.CARGO:
      return t.step3.categoriesHint;
    case STEP.CONTACT:
      return t.step4.privacyNote;
    default:
      return '';
  }
}
