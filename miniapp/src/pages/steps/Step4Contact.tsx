import { useEffect, useState } from 'react';
import WebApp from '@twa-dev/sdk';
import { usePostFormStore } from '../../store/postFormStore';
import { useT } from '../../i18n/useT';
import { Field, GhostButton, Notice, uiStyles as styles } from '../../components/primitives';
import { STEP } from '../../analytics/events';
import { telegramUser } from '../../hooks/useTelegram';
import { useSession } from '../../hooks/useSession';

interface Props {
  fieldErrors: Record<string, string>;
}

/**
 * 4-qadam — aloqa (§9.2).
 *
 * Telegram username avtomatik to'ldiriladi, telefon esa bir bosishda
 * Telegram'dan olinadi. Ikkalasi ham majburiy: bitta kanal ishlamay qolsa
 * odam ikkinchisidan yozadi — aks holda e'lon ko'rinadi-yu bitim
 * boshlanmaydi.
 *
 * Kontakt kanalda ko'rinmaydi — bu yerda aniq aytiladi, aks holda odam
 * telefonini yozishdan qo'rqadi.
 */
export function Step4Contact({ fieldErrors }: Props) {
  const t = useT();
  const state = usePostFormStore();
  const { patch } = state;

  const session = useSession();
  const [phoneNotice, setPhoneNotice] = useState<string | null>(null);

  /**
   * Username manbai — avval SERVER, keyin `initDataUnsafe`.
   *
   * Nima uchun shu tartibda: ba'zi Telegram mijozlarida `initDataUnsafe`
   * bo'sh keladi, xom `initData` satri esa to'liq bo'ladi. Server o'sha xom
   * satrni imzosi bilan parse qiladi (§7.1), ya'ni uning qiymati ham
   * ishonchliroq, ham ko'proq holatda mavjud. Ilgari faqat `initDataUnsafe`
   * o'qilardi va username umuman to'lmasdi.
   */
  const knownUsername = session.data?.username ?? telegramUser()?.username ?? null;

  /**
   * Username faqat maydon bo'sh bo'lsa to'ldiriladi — foydalanuvchi
   * o'zgartirgan qiymat bosib ketilmasligi kerak. Sessiya keyinroq kelishi
   * mumkin, shuning uchun effekt unga bog'langan.
   */
  useEffect(() => {
    if (!knownUsername || usePostFormStore.getState().contactTelegram.trim()) {
      return;
    }
    patch({ contactTelegram: knownUsername });
  }, [knownUsername, patch]);

  /**
   * Telefonni Telegram'dan olish.
   *
   * Foydalanuvchi tasdiqlasa raqam callback'ning `responseUnsafe.contact`
   * qismida keladi va input darhol to'ladi. Ilgari callback bo'sh edi —
   * odam tasdiqlasa ham maydon bo'sh qolardi va tugma ishlamayotgandek
   * tuyulardi. Eski mijozlarda javob kelmasligi mumkin, u holda qo'lda
   * kiritish qoladi va buni matn aytadi.
   */
  const requestPhoneFromTelegram = () => {
    setPhoneNotice(null);
    try {
      WebApp.requestContact?.((granted, response) => {
        const phone =
          granted && response?.status === 'sent'
            ? response.responseUnsafe?.contact?.phone_number
            : undefined;
        if (!phone) {
          setPhoneNotice(t.step4.phoneDenied);
          return;
        }
        // Telegram raqamni ko'pincha "+" siz beradi.
        const changes: Partial<{ contactPhone: string; contactTelegram: string }> = {
          contactPhone: phone.startsWith('+') ? phone : `+${phone}`,
        };
        // Kontakt javobida username YO'Q (faqat ism, familiya, raqam, id).
        // Shuning uchun uni serverdan olamiz — foydalanuvchi uchun bitta
        // bosishda ikkala maydon ham to'ladi.
        if (knownUsername && !usePostFormStore.getState().contactTelegram.trim()) {
          changes.contactTelegram = knownUsername;
        }
        patch(changes, STEP.CONTACT);
        setPhoneNotice(t.step4.phoneTaken);
      });
    } catch {
      // requestContact ba'zi versiyalarda yo'q — qo'lda kiritish qoladi.
      setPhoneNotice(t.step4.phoneDenied);
    }
  };

  /**
   * "Username yo'q" xabari faqat ishonch hosil qilgach ko'rsatiladi:
   * sessiya yuklangan, hech qaysi manbada username yo'q va maydon hamon
   * bo'sh. Aks holda yuklanish paytida bekorga qo'rqitib qo'yardik.
   */
  const missingUsername =
    !session.isLoading && !knownUsername && !state.contactTelegram.trim();

  return (
    <div className={`${styles.section} ${styles.slide}`}>
      <h2>{t.step4.title}</h2>

      <Field
        label={`${t.step4.telegram} (${t.common.required})`}
        error={
          fieldErrors.contactTelegram ??
          (missingUsername ? t.step4.noUsername : undefined)
        }
      >
        <input
          type="text"
          value={state.contactTelegram}
          aria-label={t.step4.telegram}
          maxLength={64}
          placeholder="@username"
          className={
            fieldErrors.contactTelegram || missingUsername ? styles.inputError : undefined
          }
          onChange={(event) => patch({ contactTelegram: event.target.value }, STEP.CONTACT)}
        />
      </Field>

      <Field label={`${t.step4.phone} (${t.common.required})`} error={fieldErrors.contactPhone}>
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
      {phoneNotice ? <Notice>{phoneNotice}</Notice> : null}

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

      <Notice>{t.step4.bothRequired}</Notice>
      <Notice>{t.step4.privacyNote}</Notice>
    </div>
  );
}
