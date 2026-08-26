import { useNavigate } from 'react-router-dom';
import { useSession } from '../hooks/useSession';
import { useT } from '../i18n/useT';
import { GhostButton, PrimaryButton, uiStyles as styles } from '../components/primitives';
import { useBackButton, useMainButton } from '../hooks/useTelegram';
import { LANGUAGES } from '../i18n';
import { useAppStore } from '../store/appStore';
import { useUpdateSession } from '../hooks/useSession';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import type { LanguageCode } from '../api/types';

/** Bosh sahifa: 2 ta katta tugma + til tanlovi (§9.1). */
export function HomePage() {
  const t = useT();
  const navigate = useNavigate();
  const session = useSession();
  const language = useAppStore((state) => state.language);
  const setLanguage = useAppStore((state) => state.setLanguage);
  const updateSession = useUpdateSession();

  const openForm = () => navigate('/new', { state: { entryPoint: 'home' } });

  useBackButton(null);
  useMainButton({ text: t.home.newPost, onClick: openForm });

  const changeLanguage = (next: LanguageCode) => {
    if (next === language) {
      return;
    }
    track(EV.LANGUAGE_CHANGED, { from: language, to: next });
    setLanguage(next);
    updateSession.mutate({ uiLanguage: next });
  };

  return (
    <div className="page">
      <h1>
        {t.home.greeting}
        {session.data?.firstName ? `, ${session.data.firstName}` : ''}!
      </h1>
      <p className="muted">{t.home.subtitle}</p>

      <PrimaryButton label={`📝 ${t.home.newPost}`} onClick={openForm} />

      <div className={`${styles.row} ${styles.rowFill}`}>
        <GhostButton label={`📋 ${t.home.myPosts}`} onClick={() => navigate('/my')} />
        <GhostButton
          label={`🔍 ${t.home.search}`}
          onClick={() => {
            track(EV.SEARCH_OPEN, { entry_point: 'home' });
            navigate('/search');
          }}
        />
      </div>

      <GhostButton label={`🔔 ${t.subs.title}`} onClick={() => navigate('/subscriptions')} />

      <div className={styles.card}>
        <span className={styles.label}>{t.language.title}</span>
        <div className={styles.row}>
          {LANGUAGES.map((code) => (
            <button
              key={code}
              type="button"
              aria-pressed={language === code}
              className={`${styles.chip} ${language === code ? styles.chipActive : ''}`}
              onClick={() => changeLanguage(code)}
            >
              {t.language[code]}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
