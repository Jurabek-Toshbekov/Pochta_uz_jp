/**
 * Backend DTO'lari bilan bir xil turlar (CLAUDE.md §14).
 * Backend'da DTO o'zgarsa — shu fayl ham o'zgaradi.
 */

export type PostType = 'SEND' | 'CARRY';
export type Direction = 'JP_UZ' | 'UZ_JP';
export type Currency = 'JPY' | 'USD' | 'UZS';
export type PriceUnit = 'PER_KG' | 'TOTAL' | 'NEGOTIABLE';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type UserRole = 'USER' | 'MODERATOR' | 'ADMIN';
export type VerificationLevel = 'NONE' | 'PHONE' | 'DOCUMENT';
export type PostStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CLOSED'
  | 'DELETED';

export type LanguageCode = 'uz' | 'uz-cyrl' | 'ru';

export interface SessionRequest {
  acceptTos?: boolean;
  acceptPrivacy?: boolean;
  uiLanguage?: LanguageCode;
  platform?: string;
}

export interface SessionResponse {
  username: string | null;
  firstName: string | null;
  uiLanguage: LanguageCode;
  role: UserRole;
  verificationLevel: VerificationLevel;
  trustScore: number;
  needsConsent: boolean;
  phoneVerified: boolean;
  startParam: string | null;
  isNewUser: boolean;
  serverTime: string;
}

export interface Airport {
  code: string;
  countryCode: string;
  cityUz: string;
  cityRu: string;
  cityEn: string;
  nameEn: string;
  latitude: number | null;
  longitude: number | null;
  popular: boolean;
  sortOrder: number;
}

export interface CargoCategory {
  id: number;
  code: string;
  titleUz: string;
  titleRu: string;
  emoji: string | null;
  riskLevel: RiskLevel;
  warningUz: string | null;
  sortOrder: number;
}

export interface Corridor {
  id: number;
  code: string;
  originCountry: string;
  destCountry: string;
  titleUz: string;
}

export interface ReferenceResponse {
  airports: Airport[];
  categories: CargoCategory[];
  corridors: Corridor[];
}

export interface CreatePostRequest {
  postType: PostType;
  direction: Direction;
  originAirport?: string | null;
  destAirport?: string | null;
  originCityFree?: string | null;
  destCityFree?: string | null;
  finalDestination?: string | null;
  departDate?: string | null;
  deadlineDate?: string | null;
  dateFlexibleDays?: number;
  weightKg?: number | null;
  weightKgMax?: number | null;
  priceAmount?: number | null;
  priceCurrency?: Currency | null;
  priceUnit: PriceUnit;
  categoryIds: number[];
  comment?: string | null;
  contactPhone?: string | null;
  contactTelegram?: string | null;
  contactOther?: string | null;
  safetyChecklistOk: boolean;
  sessionId?: string;
  platform?: string;
  formStartedAtMs?: number;
}

export interface PostResponse {
  id: string;
  postType: PostType;
  direction: Direction;
  originAirport: string | null;
  destAirport: string | null;
  originCityFree: string | null;
  destCityFree: string | null;
  finalDestination: string | null;
  departDate: string | null;
  deadlineDate: string | null;
  dateFlexibleDays: number;
  weightKg: number | null;
  weightKgMax: number | null;
  priceAmount: number | null;
  priceCurrency: Currency | null;
  priceUnit: PriceUnit;
  categoryIds: number[];
  comment: string | null;
  contactPhone: string | null;
  contactTelegram: string | null;
  contactOther: string | null;
  status: PostStatus;
  channelMessageId: number | null;
  channelUrl: string | null;
  deepLink: string | null;
  publishedAt: string | null;
  expiresAt: string | null;
  viewCount: number;
  contactRevealCount: number;
  createdAt: string;
}

export interface DraftRequest {
  step?: string | null;
  payload: Record<string, unknown>;
}

export interface DraftResponse {
  step: string | null;
  payload: Record<string, unknown>;
  updatedAt: string | null;
}

/** Backend `ApiErrorResponse` (§common). */
export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string> | null;
  details?: string[] | null;
  occurredAt: string;
}

export interface TrackEventPayload {
  name: string;
  sessionId?: string;
  postId?: string;
  platform?: string;
  properties?: Record<string, unknown>;
  occurredAt?: string;
}
