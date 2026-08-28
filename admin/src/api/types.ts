/**
 * Backend DTO'lari bilan bir xil turlar (§14).
 *
 * Manba: `uz.pochtajp.api.admin.dto.AdminDto` va `AnalyticsDto`.
 * Bu yerda `any` yo'q — backend nima qaytarsa, o'shanday yozilgan.
 */

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  userId: string;
  role: UserRole;
}

export type UserRole = 'USER' | 'MODERATOR' | 'ADMIN';

export type PostStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CLOSED'
  | 'DELETED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface PostRow {
  id: string;
  createdAt: string | null;
  publishedAt: string | null;
  status: PostStatus;
  postType: 'SEND' | 'CARRY';
  direction: 'JP_UZ' | 'UZ_JP';
  originAirport: string | null;
  destAirport: string | null;
  departDate: string | null;
  deadlineDate: string | null;
  priceAmount: number | null;
  priceCurrency: string | null;
  priceUnit: string | null;
  weightKg: number | null;
  riskLevel: RiskLevel;
  categories: string[];
  userId: string;
  username: string | null;
  userDisplayName: string | null;
  viewCount: number;
  contactRevealCount: number;
  reportCount: number;
  channelMessageId: number | null;
  rejectReason: string | null;
}

export interface PostDetail {
  row: PostRow;
  comment: string | null;
  finalDestination: string | null;
  originCityFree: string | null;
  destCityFree: string | null;
  safetyChecklistOk: boolean;
  contactTelegram: string | null;
  contactPhoneMasked: string | null;
  source: string | null;
}

export interface UserRow {
  id: string;
  telegramId: number;
  username: string | null;
  displayName: string | null;
  role: UserRole;
  status: 'ACTIVE' | 'LIMITED' | 'BLOCKED';
  verificationLevel: 'NONE' | 'PHONE' | 'DOCUMENT';
  trustScore: number;
  postCount: number;
  reportCount: number;
  lastSeenAt: string | null;
  createdAt: string | null;
}

export interface EventRow {
  eventName: string;
  source: string;
  properties: Record<string, unknown>;
  occurredAt: string | null;
}

export interface UserDetail {
  row: UserRow;
  uiLanguage: string | null;
  blockedReason: string | null;
  phoneVerified: boolean;
  publishedCount: number;
  revealsMade: number;
  revealsReceived: number;
  recentPosts: PostRow[];
  recentEvents: EventRow[];
}

export interface ReportRow {
  id: string;
  reason: string;
  status: 'OPEN' | 'REVIEWING' | 'RESOLVED' | 'DISMISSED';
  details: string | null;
  postId: string | null;
  reportedUserId: string | null;
  reportedUsername: string | null;
  reporterId: string;
  createdAt: string | null;
  resolvedAt: string | null;
}

export interface AuditRow {
  id: number;
  actorId: string | null;
  actorUsername: string | null;
  action: string;
  entity: string | null;
  entityId: string | null;
  payload: Record<string, unknown>;
  createdAt: string | null;
}

export type SettingValue = boolean | number | string;

export interface SettingRow {
  key: string;
  value: SettingValue;
  valueType: 'BOOLEAN' | 'NUMBER' | 'STRING';
  titleUz: string;
  descriptionUz: string | null;
  updatedAt: string | null;
}

/* --- Analitika --- */

export interface Overview {
  postsToday: number;
  postsYesterday: number;
  publishedToday: number;
  dauToday: number;
  dauYesterday: number;
  publishConversion: number;
  fillRate: number;
  openReports: number;
  pendingPosts: number;
  zeroResultRate: number;
  medianTimeToPublishSeconds: number;
}

export interface PostDailyPoint {
  date: string;
  postType: 'SEND' | 'CARRY';
  direction: 'JP_UZ' | 'UZ_JP';
  createdCount: number;
  publishedCount: number;
}

export interface FunnelStep {
  stepKey: string;
  stepIndex: number;
  usersCount: number;
  conversionFromPrevious: number;
  conversionFromStart: number;
}

export interface AbandonRow {
  lastStep: string;
  abandonCount: number;
}

export interface CohortRow {
  cohortDate: string;
  cohortSize: number;
  d1: number;
  d7: number;
  d30: number;
  d1Rate: number;
  d7Rate: number;
  d30Rate: number;
}

export interface PriceIndexPoint {
  month: string;
  direction: string;
  originAirport: string | null;
  destAirport: string | null;
  currency: string;
  sampleSize: number;
  medianPerKg: number | null;
}

export interface SupplyDemandCell {
  week: string;
  direction: string;
  carryCount: number;
  sendCount: number;
  ratio: number | null;
}

/** Bitim tasdiqlash ulushi — kontakt ochilishidan farqli o'laroq, haqiqiy natija. */
export interface DealConfirmationPoint {
  month: string;
  direction: string;
  publishedCount: number;
  confirmedCount: number;
  confirmationRate: number | null;
}

export interface CloseReasonRow {
  reason: string;
  postCount: number;
}

export interface ReviewStatsPoint {
  month: string;
  reviewCount: number;
  avgRating: number | null;
  negativeCount: number;
}

export interface MatchLatencyPoint {
  month: string;
  direction: string;
  sampleSize: number;
  medianMinutes: number | null;
}

export interface ZeroResultRoute {
  originAirport: string;
  destAirport: string;
  direction: string | null;
  postType: string | null;
  searchCount: number;
}

export interface SearchDailyPoint {
  date: string;
  searchCount: number;
  zeroResultCount: number;
  clickedCount: number;
  avgLatencyMs: number | null;
}

export interface RouteDemandSupply {
  originAirport: string | null;
  destAirport: string | null;
  searchCount: number;
  postCount: number;
}

export interface SeasonalityCell {
  dayOfWeek: number;
  monthOfYear: number;
  postType: 'SEND' | 'CARRY';
  postCount: number;
}

export interface NotificationDailyPoint {
  date: string;
  sentCount: number;
  openedCount: number;
  failedCount: number;
}

export interface NotificationStats {
  sentTotal: number;
  openedTotal: number;
  failedTotal: number;
  ctr: number;
  activeSubscriptions: number;
  daily: NotificationDailyPoint[];
}

/** Backend `ApiErrorResponse` bilan bir xil. */
export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
