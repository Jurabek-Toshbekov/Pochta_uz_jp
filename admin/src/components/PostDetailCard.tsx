import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import type { PostRow } from '../api/types';
import { formatDateTime, formatMoney } from '../lib/format';
import { Card, ErrorState, Loader, StatusBadge } from './ui';

/** Bitta e'lon tafsiloti va moderatsiya harakatlari (Moderatsiya sahifasi ostida ochiladi). */
export function PostDetailCard({ postId, onClose }: { postId: string; onClose: () => void }) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({ queryKey: ['admin-post', postId], queryFn: () => api.post(postId) });

  const invalidate = () => {
    void client.invalidateQueries({ queryKey: ['admin-posts'] });
    void client.invalidateQueries({ queryKey: ['admin-post', postId] });
    void client.invalidateQueries({ queryKey: ['overview'] });
  };

  const approve = useMutation({
    mutationFn: () => api.approvePost(postId),
    onSuccess: invalidate,
    onError: (cause) => setError(messageOf(cause)),
  });

  const reject = useMutation({
    mutationFn: () => api.rejectPost(postId, reason),
    onSuccess: () => {
      setReason('');
      invalidate();
    },
    onError: (cause) => setError(messageOf(cause)),
  });

  const close = useMutation({
    mutationFn: () => api.closePost(postId),
    onSuccess: invalidate,
    onError: (cause) => setError(messageOf(cause)),
  });

  if (detail.isLoading) {
    return <Card title="E'lon"><Loader /></Card>;
  }
  if (detail.isError || !detail.data) {
    return (
      <Card title="E'lon">
        <ErrorState message="E'lon yuklanmadi." onRetry={() => void detail.refetch()} />
      </Card>
    );
  }

  const post: PostRow = detail.data.row;

  return (
    <div style={{ marginTop: 16 }}>
      <Card
        title="E'lon tafsiloti"
        actions={<button type="button" onClick={onClose}>Yopish</button>}
      >
        <div className="grid-2">
          <div>
            <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 16px' }}>
              <dt className="muted">Holat</dt>
              <dd style={{ margin: 0 }}><StatusBadge status={post.status} /></dd>
              <dt className="muted">Egasi</dt>
              <dd style={{ margin: 0 }}>
                <Link to={`/users/${post.userId}`}>
                  {post.username ? `@${post.username}` : post.userDisplayName ?? 'Profil'}
                </Link>
              </dd>
              <dt className="muted">Yo‘nalish</dt>
              <dd className="iata" style={{ margin: 0 }}>{post.originAirport} → {post.destAirport}</dd>
              <dt className="muted">Yakuniy manzil</dt>
              <dd style={{ margin: 0 }}>{detail.data.finalDestination ?? '—'}</dd>
              <dt className="muted">Narx</dt>
              <dd className="mono" style={{ margin: 0 }}>
                {formatMoney(post.priceAmount, post.priceCurrency)} / {post.priceUnit ?? '—'}
              </dd>
              <dt className="muted">Og‘irlik</dt>
              <dd className="mono" style={{ margin: 0 }}>{post.weightKg ?? '—'} kg</dd>
              <dt className="muted">Xavfsizlik checklist</dt>
              <dd style={{ margin: 0 }}>{detail.data.safetyChecklistOk ? 'Belgilangan' : 'Yo‘q'}</dd>
              <dt className="muted">Telegram</dt>
              <dd style={{ margin: 0 }}>{detail.data.contactTelegram ?? '—'}</dd>
              <dt className="muted">Telefon</dt>
              <dd className="mono" style={{ margin: 0 }}>{detail.data.contactPhoneMasked ?? '—'}</dd>
              <dt className="muted">Yaratilgan</dt>
              <dd className="mono" style={{ margin: 0 }}>{formatDateTime(post.createdAt)}</dd>
            </dl>
          </div>

          <div>
            <div className="muted">Izoh</div>
            <p style={{ whiteSpace: 'pre-wrap', marginTop: 4 }}>{detail.data.comment ?? '—'}</p>

            {post.rejectReason && (
              <p className="error" style={{ marginTop: 12 }}>
                Rad etish sababi: {post.rejectReason}
              </p>
            )}

            <div className="toolbar" style={{ marginTop: 16 }}>
              <button
                type="button"
                className="primary"
                disabled={post.status !== 'PENDING' || approve.isPending}
                onClick={() => approve.mutate()}
              >
                Tasdiqlash va kanalga chiqarish
              </button>
              <button
                type="button"
                disabled={post.status === 'CLOSED' || close.isPending}
                onClick={() => close.mutate()}
              >
                Yopish
              </button>
            </div>

            <div className="toolbar">
              <input
                placeholder="Rad etish sababi"
                value={reason}
                aria-label="Rad etish sababi"
                onChange={(event) => setReason(event.target.value)}
                style={{ flex: 1, minWidth: 220 }}
              />
              <button
                type="button"
                className="danger"
                disabled={reason.trim().length === 0 || reject.isPending}
                onClick={() => reject.mutate()}
              >
                Rad etish
              </button>
            </div>

            {error && <p className="error">{error}</p>}
            <p className="card__note">
              Rad etilgan e'lon o‘chirilmaydi — sababi bilan bazada qoladi va foydalanuvchiga
              ko‘rinadi.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}

function messageOf(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : 'Harakat bajarilmadi.';
}
