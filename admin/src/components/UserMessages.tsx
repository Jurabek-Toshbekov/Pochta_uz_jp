import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import type { MessageRow } from '../api/types';
import { formatDateTime } from '../lib/format';
import { EmptyState, ErrorState, Loader } from './ui';

/**
 * Foydalanuvchi bilan bot orqali yozishma.
 *
 * <p>Xabar foydalanuvchi tilida "ma'muriyatdan xabar" sarlavhasi bilan boradi.
 * U 7 kun ichida botga yozgan oddiy matni shu yerda paydo bo'ladi — suhbat
 * har 10 soniyada yangilanadi.
 */

const MAX_LENGTH = 3500;
const POLL_MS = 10_000;

export function UserMessages({ userId }: { userId: string }) {
  const client = useQueryClient();
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  const thread = useQuery({
    queryKey: ['admin-messages', userId],
    queryFn: () => api.messages(userId),
    refetchInterval: POLL_MS,
  });

  const unread = (thread.data ?? []).filter((message) => message.direction === 'INBOUND' && !message.readAt).length;
  const lastId = thread.data?.[thread.data.length - 1]?.id;

  // Ochilgan suhbatdagi javoblar o'qilgan hisoblanadi — menyudagi belgi kamayadi.
  useEffect(() => {
    if (unread === 0) return;
    void api.markMessagesRead(userId).then(() => {
      void client.invalidateQueries({ queryKey: ['admin-messages', userId] });
      void client.invalidateQueries({ queryKey: ['admin-unread-messages'] });
      void client.invalidateQueries({ queryKey: ['admin-conversations'] });
    });
  }, [unread, userId, client]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'nearest' });
  }, [lastId]);

  const send = useMutation({
    mutationFn: () => api.sendMessage(userId, text.trim()),
    onSuccess: (message) => {
      setText('');
      setError(message.deliveryStatus === 'FAILED'
        ? 'Xabar saqlandi, lekin yetkazilmadi: foydalanuvchi botni bloklagan yoki o‘chirgan bo‘lishi mumkin.'
        : null);
      void client.invalidateQueries({ queryKey: ['admin-messages', userId] });
      void client.invalidateQueries({ queryKey: ['admin-conversations'] });
    },
    onError: (cause) => setError(cause instanceof ApiRequestError ? cause.message : 'Yuborilmadi. Qayta urinib ko‘ring.'),
  });

  const canSend = text.trim().length > 0 && text.length <= MAX_LENGTH && !send.isPending;

  return (
    <>
      {thread.isLoading && <Loader />}
      {thread.isError && <ErrorState message="Yozishma yuklanmadi." onRetry={() => void thread.refetch()} />}
      {thread.data && (
        <div className="chat" aria-live="polite">
          {thread.data.length === 0 && (
            <EmptyState text="Hali yozishma yo‘q. Birinchi xabar bot orqali foydalanuvchiga boradi." />
          )}
          {thread.data.map((message) => <Bubble key={message.id} message={message} />)}
          <div ref={bottom} />
        </div>
      )}

      <div className="chat__composer">
        <textarea
          value={text}
          maxLength={MAX_LENGTH}
          placeholder="Masalan: E'loningizda sana o‘tib ketgan, yangilab qo‘ying."
          aria-label="Foydalanuvchiga xabar"
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) && canSend) {
              send.mutate();
            }
          }}
        />
        <div style={{ display: 'grid', gap: 4, justifyItems: 'end' }}>
          <span className="muted mono" style={{ fontSize: 12 }}>{text.length}/{MAX_LENGTH}</span>
          <button type="button" className="primary" disabled={!canSend} onClick={() => send.mutate()}>
            Bot orqali yuborish
          </button>
        </div>
      </div>
      <p className="card__note">Ctrl+Enter — yuborish. Foydalanuvchi 7 kun ichida botga yozgan javobi shu yerda chiqadi.</p>
      {error && <p className="error">{error}</p>}
    </>
  );
}

function Bubble({ message }: { message: MessageRow }) {
  const outbound = message.direction === 'OUTBOUND';
  return (
    <div className={`chat__msg ${outbound ? 'chat__msg--out' : 'chat__msg--in'}`}>
      {message.body}
      <div className="chat__meta">
        {formatDateTime(message.createdAt)}
        {outbound && ` · ${message.adminUsername ? `@${message.adminUsername}` : 'admin'}`}
        {message.deliveryStatus === 'FAILED' && <span className="error"> · yetkazilmadi</span>}
      </div>
    </div>
  );
}
