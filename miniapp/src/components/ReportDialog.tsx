import { useState } from 'react';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { ReportReason } from '../api/types';
import { haptic } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { Card, Chip, ChipGroup, Field, GhostButton, Notice, PrimaryButton } from './primitives';

/**
 * Shikoyat oynasi (§7.3 — har bir e'londa "Shikoyat qilish" tugmasi).
 *
 * Sabab tanlash majburiy: "nimadir noto'g'ri" degan shikoyatni moderator
 * ko'rib chiqa olmaydi. Izoh ixtiyoriy.
 *
 * Matn shikoyat e'lonni yopmasligini ochiq aytadi — aks holda odam
 * "nega e'lon hali turibdi" deb o'ylaydi va ikkinchi marta yozadi.
 */

const REASONS: ReportReason[] = ['SPAM', 'SCAM', 'PROHIBITED', 'OFFENSIVE', 'OTHER'];

const DETAILS_MAX = 1000;

export function ReportDialog({ postId, onClose }: { postId: string; onClose: () => void }) {
  const t = useT();
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [details, setDetails] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const label: Record<ReportReason, string> = {
    SPAM: t.detail.reportSpam,
    SCAM: t.detail.reportScam,
    PROHIBITED: t.detail.reportProhibited,
    OFFENSIVE: t.detail.reportOffensive,
    OTHER: t.detail.reportOther,
  };

  async function submit() {
    if (!reason) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      await api.report({ postId, reason, details: details.trim() || undefined });
      haptic('medium');
      setSent(true);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : t.common.errorNetwork);
    } finally {
      setSending(false);
    }
  }

  if (sent) {
    return (
      <Card>
        <Notice>{t.detail.reportSent}</Notice>
        <GhostButton label={t.common.close} onClick={onClose} />
      </Card>
    );
  }

  return (
    <Card>
      <Notice title={t.detail.reportTitle}>{t.detail.reportHint}</Notice>

      <ChipGroup label={t.detail.reportTitle}>
        {REASONS.map((candidate) => (
          <Chip
            key={candidate}
            label={label[candidate]}
            active={reason === candidate}
            onToggle={() => setReason(candidate)}
          />
        ))}
      </ChipGroup>

      <Field label={t.detail.reportDetails} hint={t.common.optional}>
        <textarea
          value={details}
          onChange={(event) => setDetails(event.target.value.slice(0, DETAILS_MAX))}
          aria-label={t.detail.reportDetails}
          rows={3}
        />
      </Field>

      {error ? <Notice>{error}</Notice> : null}

      <PrimaryButton
        label={sending ? t.common.loading : t.detail.reportSubmit}
        disabled={!reason || sending}
        onClick={() => void submit()}
      />
      <GhostButton label={t.detail.reportCancel} onClick={onClose} />
    </Card>
  );
}
