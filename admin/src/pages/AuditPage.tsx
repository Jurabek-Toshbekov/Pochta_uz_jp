import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { EmptyState, ErrorState, Loader, Pager } from '../components/ui';
import { formatDateTime } from '../lib/format';

/**
 * Audit jurnali (§11.2 — /audit).
 *
 * <p>Faqat o'qish. Yozuv hech qachon o'zgartirilmaydi va o'chirilmaydi (§1.1).
 */
export function AuditPage() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const size = 50;

  const list = useQuery({
    queryKey: ['audit', action, page],
    queryFn: () => api.audit({ action, page, size }),
  });

  return (
    <>
      <div className="page-head">
        <h1>Audit jurnali</h1>
        <span className="muted">Kim, nima qildi, qachon</span>
      </div>

      <div className="toolbar">
        <input
          placeholder="Harakat kodi (masalan POST_APPROVE)"
          value={action}
          aria-label="Harakat kodi"
          onChange={(event) => {
            setAction(event.target.value.toUpperCase());
            setPage(0);
          }}
          style={{ minWidth: 280 }}
        />
      </div>

      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Vaqt</th>
                  <th>Kim</th>
                  <th>Harakat</th>
                  <th>Obyekt</th>
                  <th>ID</th>
                  <th style={{ minWidth: 260 }}>Tafsilot</th>
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((row) => (
                  <tr key={row.id}>
                    <td className="mono">{formatDateTime(row.createdAt)}</td>
                    <td>{row.actorUsername ? `@${row.actorUsername}` : '—'}</td>
                    <td className="mono">{row.action}</td>
                    <td className="muted">{row.entity ?? '—'}</td>
                    <td className="mono muted">{row.entityId?.slice(0, 8) ?? '—'}</td>
                    <td className="mono muted" style={{ whiteSpace: 'normal' }}>
                      {Object.keys(row.payload).length === 0
                        ? '—'
                        : JSON.stringify(row.payload)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.data.items.length === 0 && <EmptyState text="Yozuv yo‘q." />}
          </div>
          <Pager page={page} size={size} total={list.data.total} onChange={setPage} />
        </>
      )}
    </>
  );
}
