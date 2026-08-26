import { request } from './client';
import type {
  CreatePostRequest,
  DraftRequest,
  DraftResponse,
  PostResponse,
  ReferenceResponse,
  SessionRequest,
  SessionResponse,
  TrackEventPayload,
} from './types';

/** §12 dagi endpointlarning yagona ro'yxati. Komponentlar to'g'ridan-to'g'ri fetch qilmaydi. */
export const api = {
  openSession: (body: SessionRequest) =>
    request<SessionResponse>('/api/miniapp/session', { method: 'POST', body }),

  reference: () => request<ReferenceResponse>('/api/miniapp/reference'),

  createPost: (body: CreatePostRequest) =>
    request<PostResponse>('/api/miniapp/posts', { method: 'POST', body }),

  myPosts: () => request<PostResponse[]>('/api/miniapp/my/posts'),

  myPost: (id: string) => request<PostResponse>(`/api/miniapp/my/posts/${id}`),

  getDraft: () => request<DraftResponse>('/api/miniapp/drafts'),

  saveDraft: (body: DraftRequest) =>
    request<DraftResponse>('/api/miniapp/drafts', { method: 'PUT', body }),

  deleteDraft: () => request<void>('/api/miniapp/drafts', { method: 'DELETE' }),

  sendEvents: (events: TrackEventPayload[]) =>
    request<void>('/api/miniapp/events', { method: 'POST', body: { events } }),
};

export const EVENTS_PATH = '/api/miniapp/events';
