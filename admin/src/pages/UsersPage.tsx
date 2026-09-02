import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import { Card, EmptyState, ErrorState, Loader, Pager, Select, StatusBadge } from '../components/ui';
import { formatDate, formatDateTime, formatNumber } from '../lib/format';

/**
 * Foydalanuvchilar (§11.2 — /users).
 *
 * <p>Ro'yxatda telefon yo'q (§1.7). Profil sahifasida esa event lentasi
 * bor — shikoyatni tekshirishda odam nima qilganini ko'rish kerak.
 */

const ROLE_OPTIONS = [
  { value: '', label: 'Barchasi' },
  { value: 'USER', label: 'Foydalanuvchi' },
  { value: 'MODERATOR', label: 'Moderator' },
  { value: 'ADMIN', label: 'Admin' },
];

const STATUS_OPTIONS = [
  { value: '', label: 'Barchasi' },
  { value: 'ACTIVE', label: 'Faol' },
  { value: 'LIMITED', label: 'Cheklangan' },
  { value: 'BLOCKED', label: 'Bloklangan' },
];

const VERIFICATION_OPTIONS = [
  { value: 'NONE', label: 'Tasdiqlanmagan' },
  { value: 'PHONE', label: 'Telefon' },
  { value: 'DOCUMENT', label: 'Hujjat' },
];

export function UsersPage() {
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);

  const size = 25;
  const list = useQuery({
    queryKey: ['admin-users', search, role, status, page],
    queryFn: () => api.users({ search, role, status, page, size }),
  });

  return (
    <>
      <div className="page-head">
        <h1>Foydalanuvchilar</h1>
        <span className="muted">Oxirgi faollik bo‘yicha saralangan</span>
      </div>

      <div className="toolbar">
        <input
          placeholder="username yoki telegram id"
          value={search}
          aria-label="Qidiruv"
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
        <Select label="Rol" value={role} options={ROLE_OPTIONS} onChange={(value) => { setRole(value); setPage(0); }} />
        <Select label="Holat" value={status} options={STATUS_OPTIONS} onChange={(value) => { setStatus(value); setPage(0); }} />
      </div>

      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Ro‘yxat yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Foydalanuvchi</th>
                  <th className="num">Telegram ID</th>
                  <th>Rol</th>
                  <th>Holat</th>
                  <th>Tasdiq</th>
                  <th className="num">E'lon</th>
                  <th className="num">Shikoyat</th>
                  <th className="num">Trust</th>
                  <th>Oxirgi faollik</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((row) => (
                  <tr key={row.id}>
                    <td>{row.username ? `@${row.username}` : row.displayName ?? '—'}</td>
                    <td className="num">{row.telegramId}</td>
                    <td>{row.role}</td>
                    <td><StatusBadge status={row.status} /></td>
                    <td className="muted">{row.verificationLevel}</td>
                    <td className="num">{formatNumber(row.postCount)}</td>
                    <td className="num">{row.reportCount > 0 ? formatNumber(row.reportCount) : '—'}</td>
                    <td className="num">{formatNumber(row.trustScore)}</td>
                    <td className="mono">{formatDate(row.lastSeenAt)}</td>
                    <td>
                      <button type="button" onClick={() => setSelected(row.id)}>Profil</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.data.items.length === 0 && <EmptyState text="Bu filtrda foydalanuvchi yo‘q." />}
          </div>
          <Pager page={page} size={size} total={list.data.total} onChange={setPage} />
        </>
      )}

      {selected && <UserProfile userId={selected} onClose={() => setSelected(null)} />}
    </>
  );
}

function UserProfile({ userId, onClose }: { userId: string; onClose: () => void }) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [level, setLevel] = useState('PHONE');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({ queryKey: ['admin-user', userId], queryFn: () => api.user(userId) });

  const invalidate = () => {
    void client.invalidateQueries({ queryKey: ['admin-users'] });
    void client.invalidateQueries({ queryKey: ['admin-user', userId] });
  };

  const block = useMutation({
    mutationFn: () => api.blockUser(userId, reason),
    onSuccess: () => {
      setReason('');
      invalidate();
    },
    onError: (cause) => setError(cause instanceof ApiRequestError ? cause.message : 'Bajarilmadi.'),
  });

  const unblock = useMutation({
    mutationFn: () => api.unblockUser(userId),
    onSuccess: invalidate,
    onError: (cause) => setError(cause instanceof ApiRequestError ? cause.message : 'Bajarilmadi.'),
  });

  const verify = useMutation({
    mutationFn: () => api.verifyUser(userId, level),
    onSuccess: invalidate,
    onError: (cause) => setError(cause instanceof ApiRequestError ? cause.message : 'Bajarilmadi.'),
  });

  if (detail.isLoading) {
    return <Card title="Profil"><Loader /></Card>;
  }
  if (detail.isError || !detail.data) {
    return (
      <Card title="Profil">
        <ErrorState message="Profil yuklanmadi." onRetry={() => void detail.refetch()} />
      </Card>
    );
  }

  const user = detail.data;

  return (
    <div style={{ marginTop: 16 }}>
      <Card
        title={user.row.username ? `@${user.row.username}` : 'Profil'}
        actions={<button type="button" onClick={onClose}>Yopish</button>}
      >
        <div className="kpi-grid">
          <div className="card">
            <div className="kpi__label">Chiqarilgan e'lonlar</div>
            <div className="kpi__value">{formatNumber(user.publishedCount)}</div>
          </div>
          <div className="card">
            <div className="kpi__label">Kontakt ochgan</div>
            <div className="kpi__value">{formatNumber(user.revealsMade)}</div>
          </div>
          <div className="card">
            <div className="kpi__label">Kontakti ochilgan</div>
            <div className="kpi__value">{formatNumber(user.revealsReceived)}</div>
          </div>
          <div className="card">
            <div className="kpi__label">Trust score</div>
            <div className="kpi__value">{formatNumber(user.row.trustScore)}</div>
          </div>
        </div>

        {user.blockedReason && <p className="error">Bloklash sababi: {user.blockedReason}</p>}

        <div className="toolbar">
          {user.row.status === 'BLOCKED' ? (
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

        <div className="grid-2" style={{ marginTop: 16 }}>
          <div>
            <h2>Oxirgi e'lonlar</h2>
            <div className="table-wrap" style={{ marginTop: 8 }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>Sana</th>
                    <th>Holat</th>
                    <th>Yo‘nalish</th>
                  </tr>
                </thead>
                <tbody>
                  {user.recentPosts.map((post) => (
                    <tr key={post.id}>
                      <td className="mono">{formatDate(post.createdAt)}</td>
                      <td><StatusBadge status={post.status} /></td>
                      <td className="iata">{post.originAirport ?? '—'} → {post.destAirport ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {user.recentPosts.length === 0 && <EmptyState text="E'lon bermagan." />}
            </div>
          </div>

          <div>
            <h2>Harakatlar lentasi</h2>
            <div className="table-wrap" style={{ marginTop: 8, maxHeight: 320, overflowY: 'auto' }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>Vaqt</th>
                    <th>Event</th>
                    <th>Manba</th>
                  </tr>
                </thead>
                <tbody>
                  {user.recentEvents.map((event, index) => (
                    <tr key={`${event.eventName}-${index}`}>
                      <td className="mono">{formatDateTime(event.occurredAt)}</td>
                      <td className="mono">{event.eventName}</td>
                      <td className="muted">{event.source}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {user.recentEvents.length === 0 && <EmptyState text="Hali harakat yozilmagan." />}
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}
