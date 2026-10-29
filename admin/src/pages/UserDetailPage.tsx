import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/endpoints';
import type { DailyCount, UserAnalytics } from '../api/types';
import { UserActions } from '../components/UserActions';
import { UserMessages } from '../components/UserMessages';
import {
  EventBreakdownTable,
  OwnPostsTable,
  SearchesTable,
  TimelineTable,
  ViewedPostsTable,
  ViewersTable,
} from '../components/UserAnalyticsTables';
import { Card, ErrorState, KpiCard, Loader, StatusBadge } from '../components/ui';
import { formatDateTime, formatNumber, formatShortDate } from '../lib/format';

/**
 * Bitta foydalanuvchi — bitta sahifada hamma narsa (/users/:userId).
 *
 * <p>Qachon va qayerdan kelgan, nima qilgan, kimlarning e'lonlarini ochgan
 * va uning e'lonlarini kimlar ochgan. Telefon raqami ko'rsatilmaydi (§1.7);
 * sahifa ochilgani serverda audit'ga yoziladi.
 */

const INDIGO = '#2C4A8C';

export function UserDetailPage() {
  const { userId = '' } = useParams();
  const analytics = useQuery({
    queryKey: ['admin-user-analytics', userId],
    queryFn: () => api.userAnalytics(userId),
    enabled: userId.length > 0,
  });

  return (
    <>
      <div className="page-head">
        <h1>{headline(analytics.data)}</h1>
        <Link to="/users">← Foydalanuvchilar</Link>
      </div>

      {analytics.isLoading && <Loader />}
      {analytics.isError && (
        <ErrorState message="Foydalanuvchi ma'lumoti yuklanmadi." onRetry={() => void analytics.refetch()} />
      )}
      {analytics.data && <UserAnalyticsView data={analytics.data} />}
    </>
  );
}

function headline(data: UserAnalytics | undefined): string {
  if (!data) return 'Foydalanuvchi';
  const { username, displayName } = data.row;
  return username ? `@${username}${displayName ? ` · ${displayName}` : ''}` : displayName ?? 'Foydalanuvchi';
}

function UserAnalyticsView({ data }: { data: UserAnalytics }) {
  const { row, origin, totals } = data;

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Profil">
        <div className="grid-2">
          <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 16px' }}>
            <Fact label="Holat"><StatusBadge status={row.status} /> <span className="muted">{row.role}</span></Fact>
            <Fact label="Telegram ID"><span className="mono">{row.telegramId}</span></Fact>
            <Fact label="Birinchi kirgan"><span className="mono">{formatDateTime(origin.firstSeenAt)}</span></Fact>
            <Fact label="Oxirgi faollik"><span className="mono">{formatDateTime(origin.lastSeenAt)}</span></Fact>
            <Fact label="Qayerdan kelgan">{origin.referralSource ?? 'To‘g‘ridan-to‘g‘ri'}</Fact>
            <Fact label="Birinchi harakat">
              {origin.firstEvent ? (
                <span className="mono">
                  {origin.firstEvent.eventName} · {formatDateTime(origin.firstEvent.occurredAt)}
                </span>
              ) : '—'}
            </Fact>
          </dl>
          <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 16px' }}>
            <Fact label="Qurilma">{origin.platforms.join(', ') || '—'}</Fact>
            <Fact label="Til">
              {origin.uiLanguage ?? '—'} <span className="muted">(Telegram: {origin.languageCode ?? '—'})</span>
            </Fact>
            <Fact label="Premium">{origin.telegramPremium ? 'Ha' : 'Yo‘q'}</Fact>
            <Fact label="Tasdiq">{row.verificationLevel}{origin.phoneVerified ? ' · telefon tasdiqlangan' : ''}</Fact>
            <Fact label="Shartlarga rozilik"><span className="mono">{formatDateTime(origin.consentTosAt)}</span></Fact>
            <Fact label="Trust score"><span className="mono">{formatNumber(row.trustScore)}</span></Fact>
          </dl>
        </div>
        <div style={{ marginTop: 16 }}>
          <UserActions user={row} blockedReason={data.blockedReason} />
        </div>
      </Card>

      <Card title="Xabarlar (bot orqali)">
        <UserMessages userId={row.id} />
      </Card>

      <div className="kpi-grid" style={{ marginBottom: 0 }}>
        <KpiCard label="Sessiyalar" value={formatNumber(totals.sessions)} hint={`${formatNumber(totals.activeDays)} faol kun`} />
        <KpiCard
          label="E'lonlar"
          value={formatNumber(totals.postsCreated)}
          hint={`${formatNumber(totals.postsPublished)} tasi kanalga chiqqan`}
        />
        <KpiCard
          label="Qidiruvlar"
          value={formatNumber(totals.searches)}
          hint={`${formatNumber(totals.zeroResultSearches)} tasi natijasiz`}
        />
        <KpiCard
          label="Ko‘rgan e'lonlar"
          value={formatNumber(totals.postsViewed)}
          hint={`${formatNumber(totals.revealsMade)} ta kontakt ochgan`}
        />
        <KpiCard
          label="E'lonlarini ko‘rishgan"
          value={formatNumber(totals.viewsReceived)}
          hint={`${formatNumber(totals.uniqueViewers)} ta odam · ${formatNumber(totals.revealsReceived)} kontakt`}
        />
        <KpiCard
          label="Baho"
          value={totals.avgRatingReceived == null ? '—' : String(totals.avgRatingReceived)}
          hint={`${formatNumber(totals.reviewsReceived)} ta sharh`}
        />
        <KpiCard
          label="Shikoyatlar"
          value={formatNumber(totals.reportsReceived)}
          hint={`o‘zi ${formatNumber(totals.reportsMade)} ta yozgan`}
        />
        <KpiCard label="Faol obunalar" value={formatNumber(totals.activeSubscriptions)} />
      </div>

      <Card title="Faollik (90 kun)" note="Kunlik event soni. Bo‘sh kunlar — ilovani ochmagan kunlar.">
        <ActivityChart rows={data.activity} />
      </Card>

      <Card
        title="E'lonlari"
        actions={<Link to={`/posts?userId=${row.id}&status=`}>Moderatsiyada ochish →</Link>}
        note="“Unikal” — e'lonni ochgan boshqa odamlar soni (egasining o‘zi hisoblanmaydi)."
      >
        <OwnPostsTable posts={data.posts} />
      </Card>

      <div className="grid-2">
        <Card title="Uning e'lonlarini kimlar ko‘rgan" note="E'lon sahifasini ochgan yoki kontaktni ochgan odamlar.">
          <ViewersTable viewers={data.viewers} />
        </Card>
        <Card title="U kimlarning e'lonlarini ko‘rgan" note="Boshqalarning e'lonlari; kontakt ochilgani alohida belgilangan.">
          <ViewedPostsTable posts={data.viewedPosts} />
        </Card>
      </div>

      <div className="grid-2">
        <Card title="Qidiruvlari" note="Qizil — natijasiz qidiruv, ya'ni qoplanmagan talab.">
          <SearchesTable searches={data.searches} />
        </Card>
        <Card title="Harakatlar bo‘yicha">
          <EventBreakdownTable events={data.eventBreakdown} />
        </Card>
      </div>

      <Card title="Harakatlar lentasi" note="Oxirgi 100 ta event, yangisi tepada.">
        <TimelineTable events={data.timeline} />
      </Card>
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="muted">{label}</dt>
      <dd style={{ margin: 0 }}>{children}</dd>
    </>
  );
}

function ActivityChart({ rows }: { rows: DailyCount[] }) {
  if (rows.length === 0) {
    return <div className="empty">So‘nggi 90 kunda harakat yo‘q.</div>;
  }
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid stroke="#dce1e8" vertical={false} />
        <XAxis dataKey="date" tickFormatter={formatShortDate} tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
        <Tooltip labelFormatter={(value: string) => formatShortDate(value)} formatter={(value: number) => [value, 'event']} />
        <Bar dataKey="count" fill={INDIGO} />
      </BarChart>
    </ResponsiveContainer>
  );
}
