import { useState } from 'react';
import { CheckRow, PrimaryButton, uiStyles as styles } from '../components/primitives';
import { useUpdateSession } from '../hooks/useSession';
import { useT } from '../i18n/useT';

/**
 * Birinchi kirishda ToS + Privacy roziligi olinadi (§7.2).
 * Rozilik vaqti serverda bir marta yoziladi va o'zgartirilmaydi.
 */
export function ConsentGate() {
  const t = useT();
  const [tos, setTos] = useState(false);
  const [privacy, setPrivacy] = useState(false);
  const updateSession = useUpdateSession();

  const ready = tos && privacy && !updateSession.isPending;

  return (
    <div className="page">
      <h1>{t.consent.title}</h1>
      <p className="muted">{t.consent.text}</p>

      <div className={styles.card}>
        <CheckRow label={t.consent.tos} checked={tos} onChange={setTos} />
        <CheckRow label={t.consent.privacy} checked={privacy} onChange={setPrivacy} />
      </div>

      <PrimaryButton
        label={t.consent.accept}
        disabled={!ready}
        onClick={() => updateSession.mutate({ acceptTos: true, acceptPrivacy: true })}
      />
    </div>
  );
}
