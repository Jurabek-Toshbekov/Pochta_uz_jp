import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import type { PostRow } from '../api/types';
import { Card, EmptyState, ErrorState, Loader, Pager, RiskBadge, Select, StatusBadge } from '../components/ui';
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../lib/format';

/**
 * Moderatsiya (§11.2 — /posts).
 *
 * <p>Navbat tartibi serverda: {@code PENDING} va yuqori riskli kategoriyalar
 * tepada. Har bir harakat sabab bilan yoziladi va jurnalga tushadi.
 */

const STATUS_OPTIONS = [
  { value: '', label: 'Barchasi' },
  { value: 'PENDING', label: 'Kutmoqda' },
  { value: 'PUBLISHED', label: 'Chiqarilgan' },
  { value: 'REJECTED', label: 'Rad etilgan' },
  { value: 'CLOSED', label: 'Yopilgan' },
];

const TYPE_OPTIONS = [
  { value: '', label: 'Hammasi' },
  { value: 'CARRY', label: 'Olib ketaman' },
  { value: 'SEND', label: 'Yubormoqchiman' },
];

const DIRECTION_OPTIONS = [
  { value: '', label: 'Hammasi' },
  { value: 'JP_UZ', label: 'JP → UZ' },
  { value: 'UZ_JP', label: 'UZ → JP' },
];

export function PostsPage() {
  const [status, setStatus] = useState('PENDING');
  const [type, setType] = useState('');
  const [direction, setDirection] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);

  const size = 25;
  const list = useQuery({
    queryKey: ['admin-posts', status, type, direction, search, page],
    queryFn: () => api.posts({ status, type, direction, search, page, size }),
  });

  return (
    <>
      <div className="page-head">
        <h1>Moderatsiya</h1>
        <span className="muted">Kutayotgan va yuqori riskli e'lonlar tepada</span>
      </div>

      <div className="toolbar">
        <Select label="Holat" value={status} options={STATUS_OPTIONS} onChange={(value) => { setStatus(value); setPage(0); }} />
        <Select label="Tur" value={type} options={TYPE_OPTIONS} onChange={(value) => { setType(value); setPage(0); }} />
        <Select label="Yo‘nalish" value={direction} options={DIRECTION_OPTIONS} onChange={(value) => { setDirection(value); setPage(0); }} />
        <input
          placeholder="username yoki IATA kodi"
          value={search}
          aria-label="Qidiruv"
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
      </div>

      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Ro‘yxat yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Sana</th>
                  <th>Holat</th>
                  <th>Tur</th>
                  <th>Yo‘nalish</th>
                  <th>Sana (uchish)</th>
                  <th>Kategoriya</th>
                  <th className="num">Narx</th>
                  <th>Foydalanuvchi</th>
                  <th className="num">Ko‘rish</th>
                  <th className="num">Kontakt</th>
                  <th className="num">Shikoyat</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((row) => (
                  <tr key={row.id}>
                    <td className="mono">{formatDate(row.createdAt)}</td>
                    <td><StatusBadge status={row.status} /></td>
                    <td>{row.postType === 'CARRY' ? 'Olib ketadi' : 'Yuboradi'}</td>
                    <td className="iata">
                      {row.originAirport ?? '—'} → {row.destAirport ?? '—'}
                    </td>
                    <td className="mono">{formatDate(row.departDate ?? row.deadlineDate)}</td>
                    <td>
                      {row.riskLevel === 'HIGH' ? <RiskBadge level={row.riskLevel} /> : null}{' '}
                      <span className="muted">{row.categories.join(', ') || '—'}</span>
                    </td>
                    <td className="num">{formatMoney(row.priceAmount, row.priceCurrency)}</td>
                    <td>{row.username ? `@${row.username}` : row.userDisplayName ?? '—'}</td>
                    <td className="num">{formatNumber(row.viewCount)}</td>
                    <td className="num">{formatNumber(row.contactRevealCount)}</td>
                    <td className="num">{row.reportCount > 0 ? formatNumber(row.reportCount) : '—'}</td>
                    <td>
                      <button type="button" onClick={() => setSelected(row.id)}>
                        Ochish
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.data.items.length === 0 && <EmptyState text="Bu filtrda e'lon yo‘q." />}
          </div>
          <Pager page={page} size={size} total={list.data.total} onChange={setPage} />
        </>
      )}

      {selected && <PostDetailCard postId={selected} onClose={() => setSelected(null)} />}
    </>
  );
}

function PostDetailCard({ postId, onClose }: { postId: string; onClose: () => void }) {
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
