import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BoardingPassCard } from '../components/BoardingPassCard';
import { ClosePostDialog } from '../components/ClosePostDialog';
import {
  EmptyState,
  ErrorState,
  GhostButton,
  Loader,
  uiStyles as styles,
} from '../components/primitives';
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

  /** Qaysi e'lon uchun yopish oynasi ochiq. */
  const [closingId, setClosingId] = useState<string | null>(null);

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
        list.map((post) => {
          // Yopilgan yoki rad etilgan e'lon ustida qiladigan ish qolmaydi —
          // tugma ko'rsatilsa, u bosilib xato qaytarardi.
          const editable = post.status === 'PUBLISHED' || post.status === 'PENDING';

          return (
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
                <>
                  <p className={styles.hint}>
                    {post.viewCount} {t.my.views} · {post.contactRevealCount} {t.my.reveals}
                  </p>
                  {editable && closingId !== post.id ? (
                    <div className={`${styles.row} ${styles.rowFill}`}>
                      <GhostButton
                        label={t.my.edit}
                        onClick={() => navigate(`/my/${post.id}/edit`)}
                      />
                      <GhostButton label={t.my.close} onClick={() => setClosingId(post.id)} />
                    </div>
                  ) : null}
                  {closingId === post.id ? (
                    <ClosePostDialog postId={post.id} onClose={() => setClosingId(null)} />
                  ) : null}
                </>
              }
            />
          );
        })
      )}
    </div>
  );
}
