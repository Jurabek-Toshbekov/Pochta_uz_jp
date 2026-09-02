import WebApp from '@twa-dev/sdk';
import { usePostFormStore } from '../../store/postFormStore';
import { useT } from '../../i18n/useT';
import { Field, GhostButton, Notice, uiStyles as styles } from '../../components/primitives';
import { STEP } from '../../analytics/events';

interface Props {
  fieldErrors: Record<string, string>;
}

/**
 * 4-qadam — aloqa (§9.2).
 *
 * Telegram username `initData`dan avtomatik to'ldiriladi. Kontakt kanalda
 * ko'rinmaydi — bu yerda aniq aytiladi, aks holda odam telefonini yozmaydi.
 */
export function Step4Contact({ fieldErrors }: Props) {
  const t = useT();
  const state = usePostFormStore();
  const { patch } = state;

  /**
   * `requestContact` raqamni ilovaga emas, BOTGA yuboradi. Shu sabab bu tugma
   * ruxsat so'raydi, raqam esa bot tomonida `users.phone`ga yoziladi
   * (2-bosqich) va keyingi sessiyada avtomatik to'ladi. Hozircha ruxsat
   * berilmasa yoki bot hali ulanmagan bo'lsa, qo'lda kiritish qoladi.
   */
  const requestPhoneFromTelegram = () => {
    try {
      WebApp.requestContact?.(() => undefined);
    } catch {
      // requestContact ba'zi versiyalarda yo'q — qo'lda kiritish qoladi.
    }
  };

  return (
    <div className={`${styles.section} ${styles.slide}`}>
      <h2>{t.step4.title}</h2>

      <Field label={t.step4.telegram} error={fieldErrors.contactTelegram}>
        <input
          type="text"
          value={state.contactTelegram}
          aria-label={t.step4.telegram}
          maxLength={64}
          placeholder="@username"
          onChange={(event) => patch({ contactTelegram: event.target.value }, STEP.CONTACT)}
        />
      </Field>

      <Field label={`${t.step4.phone} (${t.common.optional})`} error={fieldErrors.contactPhone}>
        <input
          type="tel"
          inputMode="tel"
          value={state.contactPhone}
          aria-label={t.step4.phone}
          maxLength={32}
          placeholder={t.step4.phonePlaceholder}
          className={fieldErrors.contactPhone ? styles.inputError : undefined}
          onChange={(event) => patch({ contactPhone: event.target.value }, STEP.CONTACT)}
        />
      </Field>

      <GhostButton label={t.step4.requestPhone} onClick={requestPhoneFromTelegram} />

      <Field label={`${t.step4.other} (${t.common.optional})`} error={fieldErrors.contactOther}>
        <input
          type="text"
          value={state.contactOther}
          aria-label={t.step4.other}
          maxLength={160}
          placeholder={t.step4.otherPlaceholder}
          onChange={(event) => patch({ contactOther: event.target.value }, STEP.CONTACT)}
        />
      </Field>

      <Notice>{t.step4.privacyNote}</Notice>
    </div>
  );
}
