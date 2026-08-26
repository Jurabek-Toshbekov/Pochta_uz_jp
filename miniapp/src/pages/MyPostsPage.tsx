import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { BoardingPassCard } from '../components/BoardingPassCard';
import { EmptyState, ErrorState, Loader, uiStyles as styles } from '../components/primitives';
import { useMyPosts } from '../hooks/useMyPosts';
import { useReference } from '../hooks/useReference';
import { useBackButton } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { toBoardingPassData } from '../lib/boardingPass';
import { useLanguage } from '../store/appStore';

/** "Mening e'lonlarim" (§9.1). Bo'sh ekran — taklif, quruq xabar emas (§9.4). */
export function MyPostsPage() {
  const t = useT();
  const language = useLanguage();
  const navigate = useNavigate();
  const posts = useMyPosts();
  const reference = useReference();

  useBackButton(useCallback(() => navigate('/'), [navigate]));

  if (posts.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (posts.isError) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void posts.refetch()}
        />
      </div>
    );
  }

  const list = posts.data ?? [];

  return (
    <div className="page">
      <h1>{t.my.title}</h1>

      {list.length === 0 ? (
        <EmptyState
          message={t.my.empty}
          actionLabel={t.my.createFirst}
          onAction={() => navigate('/new', { state: { entryPoint: 'my_posts' } })}
        />
      ) : (
        list.map((post) => (
          <BoardingPassCard
            key={post.id}
            data={toBoardingPassData(
              post,
              reference.data?.airports,
              reference.data?.categories,
              language,
            )}
            status={t.status[post.status]}
            footer={
              <p className={styles.hint}>
                {post.viewCount} {t.my.views} · {post.contactRevealCount} {t.my.reveals}
              </p>
            }
          />
        ))
      )}
    </div>
  );
}
