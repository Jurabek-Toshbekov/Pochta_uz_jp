import { useQuery } from '@tanstack/react-query';
import {
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
import { Card, EmptyState, ErrorState, Loader } from '../components/ui';
import { formatNumber, formatPercent, formatShortDate, lastDays } from '../lib/format';

/**
 * Qidiruv tahlili (§11.2 — eng qimmatli sahifa).
 *
 * <p>Natijasiz qidiruv — bu qoplanmagan talab. Odam kelgan, izlagan va
 * hech narsa topmagan: aynan shu yerda o'sish imkoni yotadi.
 */

const INDIGO = '#2c4a8c';
const HANKO = '#d2352c';

export function SearchInsightsPage() {
  const range = lastDays(30);

  const zero = useQuery({ queryKey: ['zero-results'], queryFn: () => api.zeroResults(50) });
  const daily = useQuery({
    queryKey: ['search-daily', range.from],
    queryFn: () => api.searchDaily(range),
  });
  const demandSupply = useQuery({
    queryKey: ['demand-supply', range.from],
    queryFn: () => api.demandSupply({ from: range.from, limit: 25 }),
  });

  return (
    <>
      <div className="page-head">
        <h1>Qidiruv tahlili</h1>
        <span className="muted">Oxirgi 30 kun</span>
      </div>

      <Card
        title="Qidiruv salomatligi"
        note="Nol natija ulushi o‘sib borsa — bu mahsulot muammosi emas, taklif yetishmasligi. Pastdagi jadval qayerda ekanini aytadi."
      >
        {daily.isLoading && <Loader />}
        {daily.isError && <ErrorState message="Yuklanmadi." onRetry={() => void daily.refetch()} />}
        {daily.data && daily.data.length > 0 ? (
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={daily.data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
              <CartesianGrid stroke="#dce1e8" vertical={false} />
              <XAxis dataKey="date" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
              <Tooltip labelFormatter={(value: string) => formatShortDate(value)} />
              <Legend />
              <Line type="monotone" dataKey="searchCount" name="Qidiruv" stroke={INDIGO} strokeWidth={2} dot={false} />
              <Line
                type="monotone"
                dataKey="zeroResultCount"
                name="Natijasiz"
                stroke={HANKO}
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          daily.data && <EmptyState text="Bu davrda qidiruv bo‘lmagan." />
        )}
      </Card>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <Card
          title="Natijasiz qidiruvlar — top yo‘nalishlar"
          note="Bu ro‘yxat qayerga e'lon jalb qilish kerakligini to‘g‘ridan-to‘g‘ri aytadi."
        >
          {zero.isLoading && <Loader />}
          {zero.data && zero.data.length > 0 ? (
            <div className="table-wrap" style={{ maxHeight: 420, overflowY: 'auto' }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>Yo‘nalish</th>
                    <th>Tur</th>
                    <th className="num">Qidiruvlar</th>
                  </tr>
                </thead>
                <tbody>
                  {zero.data.map((row, index) => (
                    <tr key={`${row.originAirport}-${row.destAirport}-${index}`}>
                      <td className="iata">
                        {row.originAirport} → {row.destAirport}
                      </td>
                      <td className="muted">{row.postType ?? '—'}</td>
                      <td className="num">{formatNumber(row.searchCount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            zero.data && <EmptyState text="Natijasiz qidiruv yo‘q — hamma so‘rovga javob topilgan." />
          )}
        </Card>

        <Card
          title="Talab va taklif yonma-yon"
          note="Chap ustun — nechta odam qidirgan, o‘ng ustun — o‘sha yo‘nalishda nechta e'lon bor. Farq katta bo‘lgan qatorlar birinchi navbatdagi ish."
        >
          {demandSupply.isLoading && <Loader />}
          {demandSupply.data && demandSupply.data.length > 0 ? (
            <div className="table-wrap" style={{ maxHeight: 420, overflowY: 'auto' }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>Yo‘nalish</th>
                    <th className="num">Qidiruv</th>
                    <th className="num">E'lon</th>
                    <th className="num">Qoplanish</th>
                  </tr>
                </thead>
                <tbody>
                  {demandSupply.data.map((row, index) => (
                    <tr key={`${row.originAirport}-${row.destAirport}-${index}`}>
                      <td className="iata">
                        {row.originAirport ?? '?'} → {row.destAirport ?? '?'}
                      </td>
                      <td className="num">{formatNumber(row.searchCount)}</td>
                      <td className="num">{formatNumber(row.postCount)}</td>
                      <td
                        className="num"
                        style={{ color: row.postCount === 0 ? HANKO : undefined }}
                      >
                        {row.searchCount === 0
                          ? '—'
                          : formatPercent(Math.min(1, row.postCount / row.searchCount), 0)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            demandSupply.data && <EmptyState text="Ma’lumot yig‘ilmagan." />
          )}
        </Card>
      </div>
    </>
  );
}
