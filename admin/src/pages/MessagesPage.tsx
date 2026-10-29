import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/endpoints';
import { EmptyState, ErrorState, Loader } from '../components/ui';
import { formatDateTime, formatNumber } from '../lib/format';

/**
 * Barcha yozishmalar (/messages). Javob kelgan suhbatlar tepada.
 *
 * <p>Yangi suhbat foydalanuvchi profilidan boshlanadi — bu yerda faqat ro'yxat.
 */
export function MessagesPage() {
  const list = useQuery({
    queryKey: ['admin-conversations'],
    queryFn: api.conversations,
    refetchInterval: 30_000,
  });

  return (
    <>
      <div className="page-head">
        <h1>Xabarlar</h1>
        <span className="muted">Yangi xabar — Foydalanuvchilar → profil → “Xabarlar”</span>
      </div>

      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Suhbatlar yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Foydalanuvchi</th>
                <th>Oxirgi xabar</th>
                <th>Vaqt</th>
                <th className="num">Yangi</th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((conversation) => (
                <tr key={conversation.userId}>
                  <td>
                    <Link to={`/users/${conversation.userId}`}>
                      {conversation.username ? `@${conversation.username}` : conversation.displayName ?? 'Profil'}
                    </Link>
                  </td>
                  <td style={{ fontWeight: conversation.unreadCount > 0 ? 500 : 400 }}>
                    <span className="muted">{conversation.lastDirection === 'OUTBOUND' ? 'Siz: ' : ''}</span>
                    {conversation.lastBody ?? '—'}
                  </td>
                  <td className="mono">{formatDateTime(conversation.lastAt)}</td>
                  <td className="num">{conversation.unreadCount > 0 ? formatNumber(conversation.unreadCount) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data.length === 0 && (
            <EmptyState text="Hali yozishma yo‘q. Foydalanuvchi profilidan birinchi xabarni yozing." />
          )}
        </div>
      )}
    </>
  );
}
