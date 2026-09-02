import { useState } from 'react';
import { ApiError } from '../api/client';
import type { CloseReason } from '../api/types';
import { useClosePost } from '../hooks/usePostOwner';
import { hapticSuccess } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { Card, Chip, ChipGroup, GhostButton, Notice, PrimaryButton } from './primitives';

/**
 * E'lonni yopish oynasi (§6.4, 3-band).
 *
 * Sabab majburiy va uchta variant ataylab alohida turadi. "Javob bo'lmadi"
 * ni "reja o'zgardi" bilan qo'shib yuborish eng qimmatli signalni yo'q
 * qiladi: birinchisi mahsulot muammosi, ikkinchisi esa umuman bizga
 * bog'liq emas.
 */

const REASONS: CloseReason[] = ['FOUND', 'CANCELLED', 'NO_ANSWER'];

export function ClosePostDialog({
  postId,
  onClose,
  onClosed,
}: {
  postId: string;
  onClose: () => void;
  onClosed?: () => void;
}) {
  const t = useT();
  const [reason, setReason] = useState<CloseReason | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const closePost = useClosePost(postId);

  const label: Record<CloseReason, string> = {
    FOUND: t.close.found,
    CANCELLED: t.close.cancelled,
    NO_ANSWER: t.close.noAnswer,
  };

  async function submit() {
    if (!reason) {
      return;
    }
    setError(null);
    try {
      await closePost.mutateAsync({ reason });
      hapticSuccess();
      setDone(true);
      onClosed?.();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : t.common.errorNetwork);
    }
  }

  if (done) {
    return (
      <Card>
        <Notice>{t.close.done}</Notice>
        <GhostButton label={t.common.close} onClick={onClose} />
      </Card>
    );
  }

  return (
    <Card>
      <Notice title={t.close.title}>{t.close.hint}</Notice>

      <ChipGroup label={t.close.title}>
        {REASONS.map((candidate) => (
          <Chip
            key={candidate}
            label={label[candidate]}
            active={reason === candidate}
            onToggle={() => setReason(candidate)}
          />
        ))}
      </ChipGroup>

      {error ? <Notice>{error}</Notice> : null}

      <PrimaryButton
        label={closePost.isPending ? t.common.loading : t.close.submit}
        disabled={!reason || closePost.isPending}
        onClick={() => void submit()}
      />
      <GhostButton label={t.common.cancel} onClick={onClose} />
    </Card>
  );
}
