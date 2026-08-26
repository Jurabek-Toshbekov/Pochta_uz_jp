import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { SessionRequest, SessionResponse } from '../api/types';
import { getPlatform } from '../analytics/track';
import { useAppStore } from '../store/appStore';

const SESSION_KEY = ['session'];

/**
 * Sessiya — ilova ochilganda birinchi so'rov (§12).
 * Til va `startParam` shu javobdan olinadi.
 */
export function useSession() {
  const setLanguage = useAppStore((state) => state.setLanguage);
  const setStartParam = useAppStore((state) => state.setStartParam);

  return useQuery({
    queryKey: SESSION_KEY,
    queryFn: async () => {
      const session = await api.openSession({ platform: getPlatform() });
      setLanguage(session.uiLanguage);
      setStartParam(session.startParam);
      return session;
    },
    staleTime: Infinity,
    retry: 1,
  });
}

/** Rozilik va til tanlovini serverga yozadi. */
export function useUpdateSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: SessionRequest) => api.openSession(body),
    onSuccess: (session: SessionResponse) => {
      queryClient.setQueryData(SESSION_KEY, session);
    },
  });
}
