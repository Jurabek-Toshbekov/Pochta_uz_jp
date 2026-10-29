import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/endpoints';
import { EmptyState, ErrorState, Loader, Pager, Select, StatusBadge } from '../components/ui';
import { formatDate, formatNumber } from '../lib/format';

/**
 * Foydalanuvchilar (§11.2 — /users).
 *
 * <p>Ro'yxatda telefon yo'q (§1.7). Har bir qatordan to'liq analitika
 * sahifasiga o'tiladi (/users/:userId).
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

export function UsersPage() {
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

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
                    <td>
                      <Link to={`/users/${row.id}`}>{row.username ? `@${row.username}` : row.displayName ?? '—'}</Link>
                    </td>
                    <td className="num">{row.telegramId}</td>
                    <td>{row.role}</td>
                    <td><StatusBadge status={row.status} /></td>
                    <td className="muted">{row.verificationLevel}</td>
                    <td className="num">{formatNumber(row.postCount)}</td>
                    <td className="num">{row.reportCount > 0 ? formatNumber(row.reportCount) : '—'}</td>
                    <td className="num">{formatNumber(row.trustScore)}</td>
                    <td className="mono">{formatDate(row.lastSeenAt)}</td>
                    <td>
                      <Link to={`/users/${row.id}`}>Analitika →</Link>
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

    </>
  );
}
