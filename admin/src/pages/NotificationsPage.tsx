import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api/endpoints';
import { Card, EmptyState, ErrorState, KpiCard, Loader } from '../components/ui';
import { formatNumber, formatPercent, formatShortDate, lastDays } from '../lib/format';

/**
 * Xabarnomalar (§11.2 — /notifications).
 *
 * <p>Yuborish mexanizmi 5-bosqichda ishga tushadi; sahifa hozirdan
 * tayyor va obunalar sonini ko'rsatadi — ular allaqachon yig'ilyapti (§10.3).
 */

const INDIGO = '#2c4a8c';
const MIST = '#7c8899';

export function NotificationsPage() {
  const range = lastDays(30);
  const stats = useQuery({
    queryKey: ['notification-stats', range.from],
    queryFn: () => api.notificationStats(range),
  });

  if (stats.isLoading) {
    return <Loader />;
  }
  if (stats.isError || !stats.data) {
    return <ErrorState message="Yuklanmadi." onRetry={() => void stats.refetch()} />;
  }

  const data = stats.data;

  return (
    <>
      <div className="page-head">
        <h1>Xabarnomalar</h1>
        <span className="muted">Oxirgi 30 kun</span>
      </div>

      <div className="kpi-grid">
        <KpiCard label="Yuborilgan" value={formatNumber(data.sentTotal)} />
        <KpiCard label="Ochilgan" value={formatNumber(data.openedTotal)} />
        <KpiCard label="CTR" value={formatPercent(data.ctr)} hint="Ochilgan / yuborilgan" />
        <KpiCard
          label="Faol obunalar"
          value={formatNumber(data.activeSubscriptions)}
          hint="Saqlangan qidiruvlar (§10.3)"
        />
      </div>

      <Card
        title="Kunlik yuborish"
        note="CTR pasayib borsa xabarnoma spamga aylanayapti — moslik shartlarini qattiqlashtirish kerak."
      >
        {data.daily.length === 0 ? (
          <EmptyState text="Hali xabarnoma yuborilmagan (5-bosqich)." />
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={data.daily} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
              <CartesianGrid stroke="#dce1e8" vertical={false} />
              <XAxis dataKey="date" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
              <Tooltip labelFormatter={(value: string) => formatShortDate(value)} />
              <Legend />
              <Bar dataKey="sentCount" name="Yuborilgan" fill={INDIGO} />
              <Bar dataKey="openedCount" name="Ochilgan" fill={MIST} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Card>
    </>
  );
}
