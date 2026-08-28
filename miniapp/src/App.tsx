import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { EV } from './analytics/events';
import { flushOnUnload, track } from './analytics/track';
import { api } from './api/endpoints';
import { ErrorState, Loader } from './components/primitives';
import { useSession } from './hooks/useSession';
import { startParam } from './hooks/useTelegram';
import { useT } from './i18n/useT';
import { ConsentGate } from './pages/ConsentGate';
import { HomePage } from './pages/HomePage';
import { MyPostsPage } from './pages/MyPostsPage';
import { NewPostPage } from './pages/NewPostPage';
import { PreviewPage } from './pages/PreviewPage';
import { PostDetailPage } from './pages/PostDetailPage';
import { SearchPage } from './pages/SearchPage';
import { SubscriptionsPage } from './pages/SubscriptionsPage';
import { SuccessPage } from './pages/SuccessPage';

/**
 * Sessiya ochiladi, rozilik so'raladi, keyin marshrutlar (§9.1).
 * `startapp` parametri marshrutga aylantiriladi (§9.3).
 */
export function App() {
  const t = useT();
  const session = useSession();
  const navigate = useNavigate();

  // app_open — sessiyaning boshi (§6.1).
  useEffect(() => {
    if (!session.data) {
      return;
    }
    track(EV.APP_OPEN, {
      start_param: session.data.startParam,
      is_first_open: session.data.isNewUser,
      color_scheme: document.documentElement.getAttribute('data-tg-theme'),
    });
  }, [session.data]);

  // Deep link: `ch_<postId>` -> e'lon sahifasi (§8.4).
  useEffect(() => {
    const param = session.data?.startParam ?? startParam();
    if (!param) {
      return;
    }
    track(EV.DEEP_LINK_OPEN, { start_param: param });
    if (param.startsWith('ch_')) {
      // Kanaldagi post havolasi — e'lon tafsiloti (§8.4).
      navigate(`/post/${param.slice(3)}`);
    } else if (param.startsWith('nt_')) {
      // Xabarnomadagi havola. Ochilishni serverga aytamiz — CTR shundan
      // hisoblanadi (§10.3). Xato bo'lsa ham e'lon ochilaveradi.
      const notifiedPostId = param.slice(3);
      void api.notificationOpened(notifiedPostId).catch(() => undefined);
      navigate(`/post/${notifiedPostId}`);
    } else if (param === 'search') {
      navigate('/search');
    } else if (param === 'new') {
      navigate('/new', { state: { entryPoint: 'bot_button' } });
    } else if (param === 'my') {
      navigate('/my');
    }
    // Faqat bir marta — session.data kelganda.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.data?.startParam]);

  // Navbatdagi event'lar yo'qolmasligi uchun.
  useEffect(() => {
    const handler = () => flushOnUnload();
    window.addEventListener('pagehide', handler);
    return () => window.removeEventListener('pagehide', handler);
  }, []);

  if (session.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (session.isError || !session.data) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorSession}
          retryLabel={t.common.retry}
          onRetry={() => void session.refetch()}
        />
      </div>
    );
  }

  if (session.data.needsConsent) {
    return <ConsentGate />;
  }

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/new" element={<NewPostPage />} />
      <Route path="/new/preview" element={<PreviewPage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/post/:postId" element={<PostDetailPage />} />
      <Route path="/subscriptions" element={<SubscriptionsPage />} />
      <Route path="/my" element={<MyPostsPage />} />
      <Route path="/success/:postId" element={<SuccessPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
