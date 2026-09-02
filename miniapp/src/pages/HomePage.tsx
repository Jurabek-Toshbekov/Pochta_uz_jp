import { useNavigate } from 'react-router-dom';
import { useSession } from '../hooks/useSession';
import { useT } from '../i18n/useT';
import { GhostButton, PrimaryButton, uiStyles as styles } from '../components/primitives';
import { useBackButton, useMainButton } from '../hooks/useTelegram';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';

/** Bosh sahifa: 2 ta katta tugma + qolgan ekranlarga o'tish (§9.1). Til — `/profile`da. */
export function HomePage() {
  const t = useT();
  const navigate = useNavigate();
  const session = useSession();

  const openForm = () => navigate('/new', { state: { entryPoint: 'home' } });

  useBackButton(null);
  useMainButton({ text: t.home.newPost, onClick: openForm });

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

      <div className={`${styles.row} ${styles.rowFill}`}>
        <GhostButton label={`🔔 ${t.subs.title}`} onClick={() => navigate('/subscriptions')} />
        <GhostButton label={`👤 ${t.home.profile}`} onClick={() => navigate('/profile')} />
      </div>
    </div>
  );
}
