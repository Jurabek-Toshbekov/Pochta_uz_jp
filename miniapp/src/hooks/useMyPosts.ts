import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';

export function useMyPosts() {
  return useQuery({
    queryKey: ['myPosts'],
    queryFn: () => api.myPosts(),
    staleTime: 30_000,
  });
}
