import { useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import { EmptyState } from '../components/primitives';
import { useBackButton } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';

/**
 * Qidiruv ekrani — to'liq filtrlar va natijalar 3-bosqichda (§13).
 *
 * <p>Bu joy bo'sh qolmaydi: bot `/qidiruv` buyrug'i shu manzilni ochadi,
 * shuning uchun foydalanuvchi jimgina bosh sahifaga tashlanmasligi kerak.
 * Bo'sh ekran taklif bilan keladi (§9.4).
 */
export function SearchPage() {
  const t = useT();
  const navigate = useNavigate();

  useBackButton(useCallback(() => navigate('/'), [navigate]));

  useEffect(() => {
    track(EV.SEARCH_OPEN, { entry_point: 'route' });
  }, []);

  return (
    <div className="page">
      <h1>{t.home.search}</h1>
      <EmptyState
        message={t.home.searchSoon}
        actionLabel={t.home.newPost}
        onAction={() => navigate('/new', { state: { entryPoint: 'search_empty' } })}
      />
    </div>
  );
}
