import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { ClosePostRequest, PostResponse, UpdatePostRequest } from '../api/types';
import { getPlatform, getSessionId } from '../analytics/track';

/**
 * O'z e'loni ustidagi harakatlar (§9.1 — `/my/:id/edit`).
 *
 * `post_edit` va `post_close` event'larini backend yozadi (§6.1): u yerda
 * qaysi maydon haqiqatan o'zgargani va publish'dan beri necha soat
 * o'tgani ma'lum. Mijoz o'sha ma'lumotni takrorlab yuborsa, ikkita
 * manbadan ikki xil raqam chiqadi.
 */

/** Tahrirlash ekrani uchun bitta e'lon. */
export function useMyPost(postId: string | undefined) {
  return useQuery({
    queryKey: ['myPost', postId],
    queryFn: () => api.myPost(postId as string),
    enabled: Boolean(postId),
    staleTime: 30_000,
  });
}

/** Tahrir muvaffaqiyatli bo'lsa ro'yxat ham yangilanadi — eski narx qolib ketmasin. */
export function useUpdatePost(postId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: UpdatePostRequest) =>
      api.updatePost(postId, { ...body, sessionId: getSessionId(), platform: getPlatform() }),
    onSuccess: (post: PostResponse) => {
      queryClient.setQueryData(['myPost', postId], post);
      void queryClient.invalidateQueries({ queryKey: ['myPosts'] });
    },
  });
}

export function useClosePost(postId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ClosePostRequest) => api.closePost(postId, body),
    onSuccess: (post: PostResponse) => {
      queryClient.setQueryData(['myPost', postId], post);
      void queryClient.invalidateQueries({ queryKey: ['myPosts'] });
    },
  });
}
