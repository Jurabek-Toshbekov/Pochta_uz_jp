import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import { EmptyState, ErrorState, Loader, Pager, Select, StatusBadge } from '../components/ui';
import { formatDateTime } from '../lib/format';

/**
 * Shikoyatlar navbati (§11.2 — /reports).
 *
 * <p>Ochiq shikoyatlar tepada. Har bir qaror izoh bilan yoziladi:
 * "nega rad etildi" savoli keyin ham javobsiz qolmasligi kerak.
 */

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Ochiq' },
  { value: '', label: 'Barchasi' },
  { value: 'RESOLVED', label: 'Hal qilingan' },
  { value: 'DISMISSED', label: 'Rad etilgan' },
];

const REASON_OPTIONS = [
  { value: '', label: 'Barcha sabablar' },
  { value: 'SPAM', label: 'Spam' },
  { value: 'SCAM', label: 'Firibgarlik' },
  { value: 'PROHIBITED', label: 'Taqiqlangan buyum' },
  { value: 'OFFENSIVE', label: 'Haqoratomuz' },
  { value: 'OTHER', label: 'Boshqa' },
];

export function ReportsPage() {
  const client = useQueryClient();
  const [status, setStatus] = useState('OPEN');
  const [reason, setReason] = useState('');
  const [page, setPage] = useState(0);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const size = 25;
  const list = useQuery({
    queryKey: ['admin-reports', status, reason, page],
    queryFn: () => api.reports({ status, reason, page, size }),
  });

  const resolve = useMutation({
    mutationFn: (input: { id: string; resolution: 'RESOLVED' | 'DISMISSED' }) =>
      api.resolveReport(input.id, input.resolution, notes[input.id] ?? ''),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin-reports'] });
      void client.invalidateQueries({ queryKey: ['overview'] });
    },
    onError: (cause) =>
      setError(cause instanceof ApiRequestError ? cause.message : 'Qaror saqlanmadi.'),
  });

  return (
    <>
      <div className="page-head">
        <h1>Shikoyatlar</h1>
        <span className="muted">Ochiq murojaatlar navbat boshida</span>
      </div>

      <div className="toolbar">
        <Select label="Holat" value={status} options={STATUS_OPTIONS} onChange={(value) => { setStatus(value); setPage(0); }} />
        <Select label="Sabab" value={reason} options={REASON_OPTIONS} onChange={(value) => { setReason(value); setPage(0); }} />
      </div>

      {error && <p className="error">{error}</p>}
      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Ro‘yxat yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Sana</th>
                  <th>Sabab</th>
                  <th>Holat</th>
                  <th>Kimga</th>
                  <th style={{ minWidth: 260 }}>Tafsilot</th>
                  <th style={{ minWidth: 220 }}>Izoh</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((row) => (
                  <tr key={row.id}>
                    <td className="mono">{formatDateTime(row.createdAt)}</td>
                    <td>{row.reason}</td>
                    <td><StatusBadge status={row.status} /></td>
                    <td>{row.reportedUsername ? `@${row.reportedUsername}` : '—'}</td>
                    <td style={{ whiteSpace: 'normal' }}>{row.details ?? '—'}</td>
                    <td>
                      {row.status === 'OPEN' || row.status === 'REVIEWING' ? (
                        <input
                          placeholder="Qaror izohi"
                          aria-label="Qaror izohi"
                          value={notes[row.id] ?? ''}
                          onChange={(event) =>
                            setNotes((current) => ({ ...current, [row.id]: event.target.value }))
                          }
                        />
                      ) : (
                        <span className="muted">{formatDateTime(row.resolvedAt)}</span>
                      )}
                    </td>
                    <td>
                      {(row.status === 'OPEN' || row.status === 'REVIEWING') && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button
                            type="button"
                            className="primary"
                            disabled={resolve.isPending}
                            onClick={() => resolve.mutate({ id: row.id, resolution: 'RESOLVED' })}
                          >
                            Chora ko‘rildi
                          </button>
                          <button
                            type="button"
                            disabled={resolve.isPending}
                            onClick={() => resolve.mutate({ id: row.id, resolution: 'DISMISSED' })}
                          >
                            Asossiz
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.data.items.length === 0 && <EmptyState text="Shikoyat yo‘q. Yaxshi belgi." />}
          </div>
          <Pager page={page} size={size} total={list.data.total} onChange={setPage} />
        </>
      )}
    </>
  );
}
