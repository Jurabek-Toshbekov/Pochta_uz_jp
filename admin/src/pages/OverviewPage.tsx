import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api/endpoints';
import type { FunnelStep, PostDailyPoint } from '../api/types';
import { Card, ErrorState, KpiCard, Loader } from '../components/ui';
import {
  formatDuration,
  formatNumber,
  formatPercent,
  formatShortDate,
  funnelLabel,
  lastDays,
} from '../lib/format';

/**
 * Umumiy ko'rinish (§11.2).
 *
 * <p>Uchta savolga javob beradi: bugun nima bo'lyapti, mahsulot ishlayaptimi,
 * nima e'tibor talab qiladi.
 */

const INDIGO = '#2c4a8c';
const MIST = '#7c8899';
const HANKO = '#d2352c';

export function OverviewPage() {
  const range = lastDays(30);

  const overview = useQuery({ queryKey: ['overview'], queryFn: () => api.overview() });
  const daily = useQuery({
    queryKey: ['posts-daily', range.from, range.to],
    queryFn: () => api.postsDaily(range),
  });
  const funnel = useQuery({
    queryKey: ['funnel', range.from, range.to],
    queryFn: () => api.funnel(range),
  });

  if (overview.isLoading) {
    return <Loader />;
  }
  if (overview.isError || !overview.data) {
    return <ErrorState message="Ma’lumot yuklanmadi." onRetry={() => void overview.refetch()} />;
  }

  const data = overview.data;

  return (
    <>
      <div className="page-head">
        <h1>Umumiy ko‘rinish</h1>
        <span className="muted">Oxirgi 30 kun</span>
      </div>

      <div className="kpi-grid">
        <KpiCard
          label="Bugungi e'lonlar"
          value={formatNumber(data.postsToday)}
          current={data.postsToday}
          previous={data.postsYesterday}
        />
        <KpiCard
          label="Faol foydalanuvchi (DAU)"
          value={formatNumber(data.dauToday)}
          current={data.dauToday}
          previous={data.dauYesterday}
        />
        <KpiCard
          label="Publish konversiyasi"
          value={formatPercent(data.publishConversion)}
          hint="Forma ochganlarning e'lon chiqarganlari"
        />
        <KpiCard
          label="Fill rate"
          value={formatPercent(data.fillRate)}
          hint="Kamida bitta kontakt ochilgan e'lonlar"
        />
        <KpiCard
          label="Natijasiz qidiruv"
          value={formatPercent(data.zeroResultRate)}
          hint="Qoplanmagan talab — qidiruv tahliliga qarang"
        />
        <KpiCard
          label="Time to publish"
          value={formatDuration(data.medianTimeToPublishSeconds)}
          hint="Forma ochilishidan kanalgacha, mediana"
        />
        <KpiCard
          label="Kutayotgan e'lonlar"
          value={formatNumber(data.pendingPosts)}
          hint="Moderatsiya navbati"
        />
        <KpiCard
          label="Ochiq shikoyatlar"
          value={formatNumber(data.openReports)}
          hint="Hal qilinmagan murojaatlar"
        />
      </div>

      <div className="grid-2">
        <Card
          title="30 kunlik e'lonlar"
          note="SEND — pochta yubormoqchilar, CARRY — olib ketuvchilar. Ikkisi orasidagi farq talab/taklif nomutanosibligini ko‘rsatadi."
        >
          <PostsChart points={daily.data ?? []} />
        </Card>

        <Card
          title="E'lon berish voronkasi"
          note="Har bir ustun shu qadamgacha yetgan noyob foydalanuvchilar soni. Eng katta pasayish — tuzatiladigan joy."
        >
          <FunnelChart steps={funnel.data ?? []} />
        </Card>
      </div>
    </>
  );
}

function PostsChart({ points }: { points: PostDailyPoint[] }) {
  const byDate = new Map<string, { date: string; SEND: number; CARRY: number }>();
  for (const point of points) {
    const entry = byDate.get(point.date) ?? { date: point.date, SEND: 0, CARRY: 0 };
    entry[point.postType] += point.createdCount;
    byDate.set(point.date, entry);
  }
  const rows = [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));

  if (rows.length === 0) {
    return <div className="empty">Bu davrda e'lon bo‘lmagan.</div>;
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid stroke="#dce1e8" vertical={false} />
        <XAxis dataKey="date" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
        <Tooltip labelFormatter={(value: string) => formatShortDate(value)} />
        <Legend />
        <Line type="monotone" dataKey="CARRY" stroke={INDIGO} strokeWidth={2} dot={false} />
        <Line type="monotone" dataKey="SEND" stroke={MIST} strokeWidth={2} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}

function FunnelChart({ steps }: { steps: FunnelStep[] }) {
  if (steps.length === 0) {
    return <div className="empty">Voronka uchun hali event yo‘q.</div>;
  }
  const rows = steps.map((step) => ({
    name: funnelLabel(step.stepKey),
    users: step.usersCount,
    conversion: step.conversionFromPrevious,
  }));

  return (
    <>
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="#dce1e8" vertical={false} />
          <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={60} />
          <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
          <Tooltip />
          <Bar dataKey="users" fill={INDIGO} />
        </BarChart>
      </ResponsiveContainer>
      <table className="data" style={{ marginTop: 8 }}>
        <thead>
          <tr>
            <th>Qadam</th>
            <th className="num">Odam</th>
            <th className="num">Oldingi qadamdan</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step) => (
            <tr key={step.stepKey}>
              <td>{funnelLabel(step.stepKey)}</td>
              <td className="num">{formatNumber(step.usersCount)}</td>
              <td className="num" style={{ color: step.conversionFromPrevious < 0.5 ? HANKO : undefined }}>
                {formatPercent(step.conversionFromPrevious)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
