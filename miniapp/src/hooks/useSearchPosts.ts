import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { PostSearchParams, PostSearchResult, SubscriptionInput } from '../api/types';
import { getPlatform, getSessionId } from '../session';

/**
 * Keyset pagination (§10.2) — TanStack Query'ning `useInfiniteQuery`si
 * shu model uchun to'g'ri keladi: har bir sahifa oldingisining kursorini
 * oladi, offset hisoblanmaydi.
 */
export function useSearchPosts(params: PostSearchParams, enabled = true) {
  return useInfiniteQuery<PostSearchResult>({
    queryKey: ['posts', params],
    enabled,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => api.searchPosts({ ...params, cursor: pageParam as string | null }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? null,
    staleTime: 30_000,
  });
}

export function usePostDetail(postId: string | undefined) {
  return useQuery({
    queryKey: ['post', postId],
    queryFn: () => api.postDetail(postId as string),
    enabled: Boolean(postId),
    staleTime: 30_000,
  });
}

/** "Bog'lanish" — kontaktni ochadi (§6.4, 2-band). */
export function useRevealContact(postId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => api.revealContact(postId as string),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['post', postId] });
    },
  });
}

export function useSubscriptions() {
  return useQuery({
    queryKey: ['subscriptions'],
    queryFn: () => api.subscriptions(),
    staleTime: 60_000,
  });
}

export function useCreateSubscription() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: SubscriptionInput) =>
      api.createSubscription({ ...input, sessionId: getSessionId(), platform: getPlatform() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['subscriptions'] });
    },
  });
}

export function useDeleteSubscription() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.deleteSubscription(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['subscriptions'] });
    },
  });
}
