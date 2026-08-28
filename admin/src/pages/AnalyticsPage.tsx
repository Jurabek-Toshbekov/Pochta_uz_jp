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
import type { PriceIndexPoint, SeasonalityCell, SupplyDemandCell } from '../api/types';
import { Card, EmptyState, Loader } from '../components/ui';
import {
  dayName,
  formatDate,
  formatNumber,
  formatPercent,
  formatShortDate,
  lastDays,
} from '../lib/format';

/**
 * Analitika (§11.2 — /analytics).
 *
 * <p>Har bir grafik ostida bitta jumlalik izoh bor: raqam nimani anglatadi
 * va u bilan nima qilish kerak (§11.3).
 */

const INDIGO = '#2c4a8c';
const MIST = '#7c8899';
const HANKO = '#d2352c';

export function AnalyticsPage() {
  const range = lastDays(180);
  const shortRange = lastDays(30);

  const cohorts = useQuery({ queryKey: ['cohorts'], queryFn: () => api.cohorts(lastDays(90)) });
  const priceIndex = useQuery({ queryKey: ['price-index'], queryFn: () => api.priceIndex({}) });
  const supplyDemand = useQuery({
    queryKey: ['supply-demand'],
    queryFn: () => api.supplyDemand(range),
  });
  const latency = useQuery({ queryKey: ['match-latency'], queryFn: () => api.matchLatency({}) });
  const abandon = useQuery({ queryKey: ['abandon'], queryFn: () => api.abandon(shortRange) });
  const seasonality = useQuery({ queryKey: ['seasonality'], queryFn: () => api.seasonality() });
  const dealConfirmation = useQuery({
    queryKey: ['deal-confirmation'],
    queryFn: () => api.dealConfirmation({}),
  });
  const closeReasons = useQuery({ queryKey: ['close-reasons'], queryFn: () => api.closeReasons({}) });
  const reviews = useQuery({ queryKey: ['review-stats'], queryFn: () => api.reviewStats({}) });

  return (
    <>
      <div className="page-head">
        <h1>Analitika</h1>
        <span className="muted">Narx, balans va foydalanuvchi xulqi</span>
      </div>

      <div className="grid-2">
        <Card
          title="Narx indeksi"
          note="Yo‘nalish bo‘yicha kg narxining medianasi. Bu raqamning o‘zi sotiladigan mahsulot — kanalga oylik post qilib chiqarish mumkin."
        >
          {priceIndex.isLoading ? <Loader /> : <PriceIndexChart points={priceIndex.data ?? []} />}
        </Card>

        <Card
          title="Talab / taklif balansi"
          note="CARRY — joy taklif qilayotganlar, SEND — yuk yubormoqchilar. Nisbat 1 dan past bo‘lsa kuryer yetishmayapti, ya’ni o‘sish shu yerda."
        >
          {supplyDemand.isLoading ? <Loader /> : <SupplyDemandChart cells={supplyDemand.data ?? []} />}
        </Card>
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <Card
          title="Kogorta retention"
          note="Kogorta — birinchi kirgan kun. D1/D7/D30 — o‘sha odamlarning qaytish ulushi. Mahsulot yopishqoqmi degan savolga shu javob beradi."
        >
          {cohorts.isLoading ? (
            <Loader />
          ) : cohorts.data && cohorts.data.length > 0 ? (
            <div className="table-wrap" style={{ maxHeight: 320, overflowY: 'auto' }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>Kogorta</th>
                    <th className="num">Hajmi</th>
                    <th className="num">D1</th>
                    <th className="num">D7</th>
                    <th className="num">D30</th>
                  </tr>
                </thead>
                <tbody>
                  {cohorts.data.map((row) => (
                    <tr key={row.cohortDate}>
                      <td className="mono">{formatDate(row.cohortDate)}</td>
                      <td className="num">{formatNumber(row.cohortSize)}</td>
                      <td className="num">{formatPercent(row.d1Rate, 0)}</td>
                      <td className="num">{formatPercent(row.d7Rate, 0)}</td>
                      <td className="num">{formatPercent(row.d30Rate, 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState text="Kogorta uchun ma’lumot yetarli emas." />
          )}
        </Card>

        <Card
          title="Match latency"
          note="E'lon kanalga chiqqandan birinchi kontakt ochilgunicha o‘tgan vaqt medianasi. Tezlik — foydalanuvchi uchun asosiy qiymat."
        >
          {latency.isLoading ? (
            <Loader />
          ) : latency.data && latency.data.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={latency.data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid stroke="#dce1e8" vertical={false} />
                <XAxis dataKey="month" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line type="monotone" dataKey="medianMinutes" stroke={INDIGO} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState text="Hali kontakt ochilmagan." />
          )}
        </Card>
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <Card
          title="Bitim tasdiqlash ulushi"
          note="Kontakt ochilishi — faqat niyat. Bu raqam esa natija: e'lon egasi “Odam topildi” deb tasdiqlagan ulush. Ikkisi orasidagi katta farq — muzokara bosqichida uzilish."
        >
          {dealConfirmation.isLoading ? (
            <Loader />
          ) : dealConfirmation.data && dealConfirmation.data.length > 0 ? (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Oy</th>
                    <th>Yo‘nalish</th>
                    <th className="num">Chiqarilgan</th>
                    <th className="num">Tasdiqlangan</th>
                    <th className="num">Ulush</th>
                  </tr>
                </thead>
                <tbody>
                  {dealConfirmation.data.map((row) => (
                    <tr key={`${row.month}-${row.direction}`}>
                      <td className="mono">{formatDate(row.month)}</td>
                      <td>{row.direction}</td>
                      <td className="num">{formatNumber(row.publishedCount)}</td>
                      <td className="num">{formatNumber(row.confirmedCount)}</td>
                      <td className="num">{formatPercent(row.confirmationRate ?? 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState text="Hali bitim tasdiqlanmagan (“Odam topdingizmi?” so‘rovi 3 kundan keyin ketadi)." />
          )}
        </Card>

        <Card
          title="Yopish sabablari"
          note="“Javob bo‘lmadi” ulushi katta bo‘lsa — bu mahsulot muammosi: e’lon ko‘rinyapti, lekin natija bermayapti."
        >
          {closeReasons.isLoading ? (
            <Loader />
          ) : closeReasons.data && closeReasons.data.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={closeReasons.data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid stroke="#dce1e8" vertical={false} />
                <XAxis dataKey="reason" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="postCount" fill={MIST} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState text="Hali yopilgan e'lon yo‘q." />
          )}
        </Card>
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <Card
          title="Reytting"
          note="Past baholar o‘sib borsa ishonch tushadi — moderatsiyani kuchaytirish vaqti keldi."
        >
          {reviews.isLoading ? (
            <Loader />
          ) : reviews.data && reviews.data.length > 0 ? (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Oy</th>
                    <th className="num">Baholar</th>
                    <th className="num">O‘rtacha</th>
                    <th className="num">Salbiy (1–2)</th>
                  </tr>
                </thead>
                <tbody>
                  {reviews.data.map((row) => (
                    <tr key={row.month}>
                      <td className="mono">{formatDate(row.month)}</td>
                      <td className="num">{formatNumber(row.reviewCount)}</td>
                      <td className="num">{row.avgRating ?? '—'}</td>
                      <td className="num" style={{ color: row.negativeCount > 0 ? HANKO : undefined }}>
                        {formatNumber(row.negativeCount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState text="Hali baho qoldirilmagan." />
          )}
        </Card>

        <Card
          title="Qaysi qadamda tashlab ketishadi"
          note="Eng ko‘p tashlab ketilgan qadam — formani soddalashtirish kerak bo‘lgan joy."
        >
          {abandon.isLoading ? (
            <Loader />
          ) : abandon.data && abandon.data.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={abandon.data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid stroke="#dce1e8" vertical={false} />
                <XAxis dataKey="lastStep" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="abandonCount" fill={HANKO} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState text="Tashlab ketish yozilmagan." />
          )}
        </Card>

        <Card
          title="Mavsumiylik"
          note="Hafta kunlari bo‘yicha e'lonlar. Ta’til va bayramlar oldidan o‘sish ko‘rinadi — xabarnoma va kanal postlarini shunga moslash kerak."
        >
          {seasonality.isLoading ? <Loader /> : <SeasonalityChart cells={seasonality.data ?? []} />}
        </Card>
      </div>
    </>
  );
}

function PriceIndexChart({ points }: { points: PriceIndexPoint[] }) {
  if (points.length === 0) {
    return <EmptyState text="Narx indeksi uchun e'lon yetarli emas." />;
  }
  const byMonth = new Map<string, { month: string; value: number; count: number }>();
  for (const point of points) {
    if (point.medianPerKg === null) {
      continue;
    }
    const entry = byMonth.get(point.month) ?? { month: point.month, value: 0, count: 0 };
    entry.value += point.medianPerKg;
    entry.count += 1;
    byMonth.set(point.month, entry);
  }
  const rows = [...byMonth.values()]
    .map((entry) => ({ month: entry.month, median: Math.round(entry.value / entry.count) }))
    .sort((a, b) => a.month.localeCompare(b.month));

  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
        <CartesianGrid stroke="#dce1e8" vertical={false} />
        <XAxis dataKey="month" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} />
        <Tooltip />
        <Line type="monotone" dataKey="median" stroke={INDIGO} strokeWidth={2} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}

function SupplyDemandChart({ cells }: { cells: SupplyDemandCell[] }) {
  if (cells.length === 0) {
    return <EmptyState text="Balans uchun ma’lumot yo‘q." />;
  }
  const byWeek = new Map<string, { week: string; CARRY: number; SEND: number }>();
  for (const cell of cells) {
    const entry = byWeek.get(cell.week) ?? { week: cell.week, CARRY: 0, SEND: 0 };
    entry.CARRY += cell.carryCount;
    entry.SEND += cell.sendCount;
    byWeek.set(cell.week, entry);
  }
  const rows = [...byWeek.values()].sort((a, b) => a.week.localeCompare(b.week));

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid stroke="#dce1e8" vertical={false} />
        <XAxis dataKey="week" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
        <Tooltip />
        <Legend />
        <Bar dataKey="CARRY" fill={INDIGO} />
        <Bar dataKey="SEND" fill={MIST} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function SeasonalityChart({ cells }: { cells: SeasonalityCell[] }) {
  if (cells.length === 0) {
    return <EmptyState text="Ma’lumot yo‘q." />;
  }
  const byDay = new Map<number, { day: string; count: number }>();
  for (const cell of cells) {
    const entry = byDay.get(cell.dayOfWeek) ?? { day: dayName(cell.dayOfWeek), count: 0 };
    entry.count += cell.postCount;
    byDay.set(cell.dayOfWeek, entry);
  }
  const rows = [...byDay.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([, value]) => value);

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid stroke="#dce1e8" vertical={false} />
        <XAxis dataKey="day" tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
        <Tooltip />
        <Bar dataKey="count" fill={INDIGO} />
      </BarChart>
    </ResponsiveContainer>
  );
}
