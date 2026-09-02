import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { Profile, UpdateProfileRequest } from '../api/types';
import { useAppStore } from '../store/appStore';

const PROFILE_KEY = ['profile'];

/** Profil ekrani (§9.1). Telefon shu yerda keladi, sessiya javobida emas. */
export function useProfile() {
  return useQuery({
    queryKey: PROFILE_KEY,
    queryFn: () => api.profile(),
    staleTime: 30_000,
  });
}

/**
 * Til va telefon.
 *
 * Til serverda saqlanadi va keyingi ochilishda sessiya javobidan keladi —
 * shu sabab local store ham darhol yangilanadi, aks holda ekran eski tilda
 * qolib, faqat qayta ochilgandan keyin o'zgarardi.
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const setLanguage = useAppStore((state) => state.setLanguage);

  return useMutation({
    mutationFn: (body: UpdateProfileRequest) => api.updateProfile(body),
    onSuccess: (profile: Profile) => {
      queryClient.setQueryData(PROFILE_KEY, profile);
      setLanguage(profile.uiLanguage);
      void queryClient.invalidateQueries({ queryKey: ['session'] });
    },
  });
}
