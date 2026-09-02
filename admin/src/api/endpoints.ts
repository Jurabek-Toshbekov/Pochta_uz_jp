import { query, request } from './client';
import type {
  AbandonRow,
  AuditRow,
  CloseReasonRow,
  CohortRow,
  DealConfirmationPoint,
  FunnelStep,
  LoginResponse,
  MatchLatencyPoint,
  NotificationStats,
  Overview,
  Page,
  PostDailyPoint,
  PostDetail,
  PostRow,
  PriceIndexPoint,
  ReportRow,
  ReviewStatsPoint,
  RouteDemandSupply,
  SearchDailyPoint,
  SeasonalityCell,
  SettingRow,
  SettingValue,
  SupplyDemandCell,
  UserDetail,
  UserRow,
  ZeroResultRoute,
} from './types';

/**
 * Admin API endpoint'lari (§12).
 *
 * <p>Har bir yo'l shu yerda bir marta yoziladi — sahifalar `fetch`ni
 * to'g'ridan-to'g'ri chaqirmaydi.
 */

const BASE = '/api/admin';

/**
 * Sana oralig'i. `interface` emas, `type`: TypeScript'da faqat type alias'da
 * yashirin index signature bo'ladi va u `query()` ga uzatiladi.
 */
export type DateRange = {
  from?: string;
  to?: string;
};

export const api = {
  /* --- Kirish --- */

  login(code: string) {
    return request<LoginResponse>(`${BASE}/auth/telegram`, {
      method: 'POST',
      body: { code },
      anonymous: true,
    });
  },

  /* --- Umumiy ko'rinish --- */

  overview() {
    return request<Overview>(`${BASE}/overview`);
  },

  /* --- E'lonlar --- */

  posts(params: {
    status?: string;
    type?: string;
    direction?: string;
    search?: string;
    page?: number;
    size?: number;
  }) {
    return request<Page<PostRow>>(`${BASE}/posts${query(params)}`);
  },

  post(postId: string) {
    return request<PostDetail>(`${BASE}/posts/${postId}`);
  },

  approvePost(postId: string) {
    return request<PostDetail>(`${BASE}/posts/${postId}/approve`, { method: 'POST' });
  },

  rejectPost(postId: string, reason: string) {
    return request<PostDetail>(`${BASE}/posts/${postId}/reject`, {
      method: 'POST',
      body: { reason },
    });
  },

  closePost(postId: string) {
    return request<PostDetail>(`${BASE}/posts/${postId}/close`, { method: 'POST' });
  },

  updatePost(postId: string, body: { comment?: string; finalDestination?: string }) {
    return request<PostDetail>(`${BASE}/posts/${postId}`, { method: 'PATCH', body });
  },

  /* --- Foydalanuvchilar --- */

  users(params: { search?: string; role?: string; status?: string; page?: number; size?: number }) {
    return request<Page<UserRow>>(`${BASE}/users${query(params)}`);
  },

  user(userId: string) {
    return request<UserDetail>(`${BASE}/users/${userId}`);
  },

  blockUser(userId: string, reason: string) {
    return request<UserDetail>(`${BASE}/users/${userId}/block`, { method: 'POST', body: { reason } });
  },

  unblockUser(userId: string) {
    return request<UserDetail>(`${BASE}/users/${userId}/unblock`, { method: 'POST' });
  },

  verifyUser(userId: string, level: string) {
    return request<UserDetail>(`${BASE}/users/${userId}/verify`, { method: 'POST', body: { level } });
  },

  /* --- Shikoyatlar --- */

  reports(params: { status?: string; reason?: string; page?: number; size?: number }) {
    return request<Page<ReportRow>>(`${BASE}/reports${query(params)}`);
  },

  resolveReport(reportId: string, resolution: 'RESOLVED' | 'DISMISSED', note: string) {
    return request<ReportRow>(`${BASE}/reports/${reportId}/resolve`, {
      method: 'POST',
      body: { resolution, note },
    });
  },

  /* --- Analitika --- */

  postsDaily(range: DateRange) {
    return request<PostDailyPoint[]>(`${BASE}/analytics/posts-daily${query(range)}`);
  },

  funnel(range: DateRange) {
    return request<FunnelStep[]>(`${BASE}/analytics/funnel${query(range)}`);
  },

  abandon(range: DateRange) {
    return request<AbandonRow[]>(`${BASE}/analytics/abandon${query(range)}`);
  },

  cohorts(range: DateRange) {
    return request<CohortRow[]>(`${BASE}/analytics/cohorts${query(range)}`);
  },

  priceIndex(params: DateRange & { direction?: string }) {
    return request<PriceIndexPoint[]>(`${BASE}/analytics/price-index${query(params)}`);
  },

  supplyDemand(range: DateRange) {
    return request<SupplyDemandCell[]>(`${BASE}/analytics/supply-demand${query(range)}`);
  },

  matchLatency(range: DateRange) {
    return request<MatchLatencyPoint[]>(`${BASE}/analytics/match-latency${query(range)}`);
  },

  dealConfirmation(range: DateRange) {
    return request<DealConfirmationPoint[]>(`${BASE}/analytics/deal-confirmation${query(range)}`);
  },

  closeReasons(range: DateRange) {
    return request<CloseReasonRow[]>(`${BASE}/analytics/close-reasons${query(range)}`);
  },

  reviewStats(range: DateRange) {
    return request<ReviewStatsPoint[]>(`${BASE}/analytics/reviews${query(range)}`);
  },

  seasonality() {
    return request<SeasonalityCell[]>(`${BASE}/analytics/seasonality`);
  },

  /* --- Qidiruv tahlili --- */

  zeroResults(limit = 50) {
    return request<ZeroResultRoute[]>(`${BASE}/search-insights/zero-results${query({ limit })}`);
  },

  searchDaily(range: DateRange) {
    return request<SearchDailyPoint[]>(`${BASE}/search-insights/daily${query(range)}`);
  },

  demandSupply(params: DateRange & { limit?: number }) {
    return request<RouteDemandSupply[]>(`${BASE}/search-insights/demand-supply${query(params)}`);
  },

  /* --- Xabarnomalar --- */

  notificationStats(range: DateRange) {
    return request<NotificationStats>(`${BASE}/notifications/stats${query(range)}`);
  },

  /* --- Sozlamalar va audit --- */

  settings() {
    return request<SettingRow[]>(`${BASE}/settings`);
  },

  updateSetting(key: string, value: SettingValue) {
    return request<SettingRow>(`${BASE}/settings/${key}`, { method: 'PATCH', body: { value } });
  },

  audit(params: { action?: string; actorId?: string; page?: number; size?: number }) {
    return request<Page<AuditRow>>(`${BASE}/audit${query(params)}`);
  },
};
