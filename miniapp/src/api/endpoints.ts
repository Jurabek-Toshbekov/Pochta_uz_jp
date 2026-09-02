import { request } from './client';
import type {
  ClosePostRequest,
  Contact,
  CreatePostRequest,
  DraftRequest,
  DraftResponse,
  PostDetail,
  PostResponse,
  PostSearchParams,
  PostSearchResult,
  Profile,
  ReferenceResponse,
  ReportInput,
  ReviewInput,
  SessionRequest,
  SessionResponse,
  Subscription,
  SubscriptionInput,
  TrackEventPayload,
  TrustResponse,
  UpdatePostRequest,
  UpdateProfileRequest,
} from './types';

/** Filtrlarni query-string'ga o'giradi. Bo'sh qiymatlar tashlab yuboriladi. */
export function toSearchQuery(params: PostSearchParams): string {
  const query = new URLSearchParams();
  const put = (key: string, value: unknown) => {
    if (value === null || value === undefined || value === '' ) {
      return;
    }
    if (Array.isArray(value)) {
      if (value.length > 0) {
        query.set(key, value.join(','));
      }
      return;
    }
    if (typeof value === 'boolean') {
      if (value) {
        query.set(key, 'true');
      }
      return;
    }
    query.set(key, String(value));
  };

  put('type', params.type);
  put('direction', params.direction);
  put('origin', params.origin);
  put('dest', params.dest);
  put('dateFrom', params.dateFrom);
  put('dateTo', params.dateTo);
  put('categories', params.categories);
  put('priceMax', params.priceMax);
  put('currency', params.currency);
  put('verifiedOnly', params.verifiedOnly);
  put('q', params.q);
  put('sort', params.sort);
  put('cursor', params.cursor);
  put('size', params.size);

  const text = query.toString();
  return text ? `?${text}` : '';
}

/** §12 dagi endpointlarning yagona ro'yxati. Komponentlar to'g'ridan-to'g'ri fetch qilmaydi. */
export const api = {
  openSession: (body: SessionRequest) =>
    request<SessionResponse>('/api/miniapp/session', { method: 'POST', body }),

  reference: () => request<ReferenceResponse>('/api/miniapp/reference'),

  createPost: (body: CreatePostRequest) =>
    request<PostResponse>('/api/miniapp/posts', { method: 'POST', body }),

  myPosts: () => request<PostResponse[]>('/api/miniapp/my/posts'),

  searchPosts: (params: PostSearchParams) =>
    request<PostSearchResult>(`/api/miniapp/posts${toSearchQuery(params)}`),

  postDetail: (id: string) => request<PostDetail>(`/api/miniapp/posts/${id}`),

  revealContact: (id: string) =>
    request<Contact>(`/api/miniapp/posts/${id}/reveal-contact`, { method: 'POST' }),

  subscriptions: () => request<Subscription[]>('/api/miniapp/subscriptions'),

  createSubscription: (body: SubscriptionInput) =>
    request<Subscription>('/api/miniapp/subscriptions', { method: 'POST', body }),

  deleteSubscription: (id: string) =>
    request<void>(`/api/miniapp/subscriptions/${id}`, { method: 'DELETE' }),

  myPost: (id: string) => request<PostResponse>(`/api/miniapp/my/posts/${id}`),

  /** O'z e'lonini tahrirlash. Berilmagan maydonlarga tegilmaydi. */
  updatePost: (id: string, body: UpdatePostRequest) =>
    request<PostResponse>(`/api/miniapp/posts/${id}`, { method: 'PATCH', body }),

  /** E'lonni sabab bilan yopish (§6.4, 3-band). */
  closePost: (id: string, body: ClosePostRequest) =>
    request<PostResponse>(`/api/miniapp/posts/${id}/close`, { method: 'POST', body }),

  profile: () => request<Profile>('/api/miniapp/me'),

  updateProfile: (body: UpdateProfileRequest) =>
    request<Profile>('/api/miniapp/me', { method: 'PATCH', body }),

  getDraft: () => request<DraftResponse>('/api/miniapp/drafts'),

  saveDraft: (body: DraftRequest) =>
    request<DraftResponse>('/api/miniapp/drafts', { method: 'PUT', body }),

  deleteDraft: () => request<void>('/api/miniapp/drafts', { method: 'DELETE' }),

  /** Shikoyat (§7.3). E'lon darhol yopilmaydi — qaror moderatorniki. */
  report: (body: ReportInput) =>
    request<TrustResponse>('/api/miniapp/reports', { method: 'POST', body }),

  /** Bitim sherigiga baho (§6.4, 7-band). */
  review: (body: ReviewInput) =>
    request<TrustResponse>('/api/miniapp/reviews', { method: 'POST', body }),

  /** Xabarnomadagi havola ochildi — CTR shundan hisoblanadi. */
  notificationOpened: (postId: string) =>
    request<void>('/api/miniapp/notifications/opened', { method: 'POST', body: { postId } }),

  sendEvents: (events: TrackEventPayload[]) =>
    request<void>('/api/miniapp/events', { method: 'POST', body: { events } }),
};

export const EVENTS_PATH = '/api/miniapp/events';
