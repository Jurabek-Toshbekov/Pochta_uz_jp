import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import type { UserRow } from '../api/types';
import { Select } from './ui';

/**
 * Foydalanuvchiga moderatsiya harakatlari: bloklash va tasdiqlash darajasi.
 * Har biri serverda {@code moderation_actions} va audit'ga yoziladi.
 */

const VERIFICATION_OPTIONS = [
  { value: 'NONE', label: 'Tasdiqlanmagan' },
  { value: 'PHONE', label: 'Telefon' },
  { value: 'DOCUMENT', label: 'Hujjat' },
];

export function UserActions({ user, blockedReason }: { user: UserRow; blockedReason: string | null }) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [level, setLevel] = useState<string>(user.verificationLevel === 'NONE' ? 'PHONE' : user.verificationLevel);
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => {
    setError(null);
    void client.invalidateQueries({ queryKey: ['admin-users'] });
    void client.invalidateQueries({ queryKey: ['admin-user-analytics', user.id] });
  };
  const onError = (cause: unknown) => setError(cause instanceof ApiRequestError ? cause.message : 'Bajarilmadi.');

  const block = useMutation({
    mutationFn: () => api.blockUser(user.id, reason),
    onSuccess: () => {
      setReason('');
      invalidate();
    },
    onError,
  });
  const unblock = useMutation({ mutationFn: () => api.unblockUser(user.id), onSuccess: invalidate, onError });
  const verify = useMutation({ mutationFn: () => api.verifyUser(user.id, level), onSuccess: invalidate, onError });

  return (
    <>
      {blockedReason && <p className="error">Bloklash sababi: {blockedReason}</p>}
      <div className="toolbar">
        {user.status === 'BLOCKED' ? (
          <button type="button" disabled={unblock.isPending} onClick={() => unblock.mutate()}>
            Blokdan chiqarish
          </button>
        ) : (
          <>
            <input
              placeholder="Bloklash sababi"
              value={reason}
              aria-label="Bloklash sababi"
              onChange={(event) => setReason(event.target.value)}
              style={{ minWidth: 220 }}
            />
            <button
              type="button"
              className="danger"
              disabled={reason.trim().length === 0 || block.isPending}
              onClick={() => block.mutate()}
            >
              Bloklash
            </button>
          </>
        )}
        <Select label="Tasdiq" value={level} options={VERIFICATION_OPTIONS} onChange={setLevel} />
        <button type="button" disabled={verify.isPending} onClick={() => verify.mutate()}>
          Darajani o‘rnatish
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </>
  );
}
