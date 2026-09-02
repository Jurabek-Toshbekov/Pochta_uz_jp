import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { CreatePostRequest, PostResponse } from '../api/types';

/**
 * E'lonni yuborish. Muvaffaqiyatda "Mening e'lonlarim" va draft keshi
 * yangilanadi — backend draftni allaqachon tozalagan.
 */
export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation<PostResponse, Error, CreatePostRequest>({
    mutationFn: (body) => api.createPost(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['myPosts'] });
      queryClient.setQueryData(['draft'], { step: null, payload: {}, updatedAt: null });
    },
  });
}
