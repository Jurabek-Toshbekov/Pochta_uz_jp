import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { toDraftPayload, usePostFormStore } from '../store/postFormStore';

const AUTOSAVE_DEBOUNCE_MS = 1200;

/**
 * Draft autosave (§6.4, 5-band): "Har o'zgarishda saqla. Tashlab ketilgan draft —
 * voronkadagi teshikning fotosurati."
 *
 * Har bir tugma bosishda so'rov yubormaslik uchun 1.2 soniya kutiladi.
 */
export function useDraftAutosave(step: string, active: boolean): void {
  const state = usePostFormStore();
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastPayload = useRef<string>('');

  useEffect(() => {
    if (!active) {
      return;
    }
    const payload = toDraftPayload(state);
    const serialized = JSON.stringify(payload);
    if (serialized === lastPayload.current) {
      return;
    }

    if (timer.current) {
      clearTimeout(timer.current);
    }
    timer.current = setTimeout(() => {
      lastPayload.current = serialized;
      // Draft saqlanmasa forma ishlashda davom etadi — bu yordamchi funksiya.
      void api.saveDraft({ step, payload }).catch(() => undefined);
    }, AUTOSAVE_DEBOUNCE_MS);

    return () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    };
  }, [state, step, active]);
}

/** Ilova ochilganda draftni tiklaydi — forma o'sha joydan davom etadi (§9.5). */
export function useDraftRestore(enabled: boolean) {
  const hydrate = usePostFormStore((store) => store.hydrate);
  const restored = useRef(false);

  const query = useQuery({
    queryKey: ['draft'],
    queryFn: () => api.getDraft(),
    enabled,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (!restored.current && query.data && Object.keys(query.data.payload).length > 0) {
      restored.current = true;
      hydrate(query.data.payload);
    }
  }, [query.data, hydrate]);

  return query;
}
