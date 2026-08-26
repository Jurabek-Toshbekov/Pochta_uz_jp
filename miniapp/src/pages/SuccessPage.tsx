import { useCallback, useEffect } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { PostResponse } from '../api/types';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import { BoardingPassCard, type BoardingPassData } from '../components/BoardingPassCard';
import { GhostButton, Loader, PrimaryButton, uiStyles as styles } from '../components/primitives';
import { useReference } from '../hooks/useReference';
import { openLink, shareUrl, useBackButton, useMainButton } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';

/**
 * Muvaffaqiyat ekrani (§9.2). Karta ustiga hanko muhri bosiladi — loyihaning
 * esda qoladigan yagona animatsiyasi (§9.4).
 *
 * Kanalga yuborilmagan holat ham shu ekranda ko'rsatiladi: e'lon saqlangan,
 * qaytadan to'ldirish kerak emas (§1.1).
 */
export function SuccessPage() {
  const t = useT();
  const navigate = useNavigate();
  const { postId = '' } = useParams();
  const location = useLocation();
  const reference = useReference();

  const passed = (location.state as { post?: PostResponse } | null)?.post;

  const query = useQuery({
    queryKey: ['myPost', postId],
    queryFn: () => api.myPost(postId),
    enabled: !passed && postId.length > 0,
    initialData: passed,
  });

  const post = query.data;
  const published = post?.status === 'PUBLISHED';

  useEffect(() => {
    if (post) {
      track(EV.POST_VIEW, { source: 'success_screen' }, post.id);
    }
  }, [post]);

  const goHome = useCallback(() => navigate('/'), [navigate]);
  useBackButton(goHome);
  useMainButton({ text: t.success.toHome, onClick: goHome });

  if (!post) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  const categories = (reference.data?.categories ?? []).filter((category) =>
    post.categoryIds.includes(category.id),
  );
  const airports = reference.data?.airports ?? [];

  const data: BoardingPassData = {
    postType: post.postType,
    originCode: post.originAirport,
    destCode: post.destAirport,
    originCityFree: post.originCityFree,
    destCityFree: post.destCityFree,
    originCity: airports.find((a) => a.code === post.originAirport)?.cityUz ?? null,
    destCity: airports.find((a) => a.code === post.destAirport)?.cityUz ?? null,
    finalDestination: post.finalDestination,
    date: post.departDate ?? post.deadlineDate,
    flexibleDays: post.dateFlexibleDays,
    weightKg: post.weightKg,
    weightKgMax: post.weightKgMax,
    priceAmount: post.priceAmount,
    priceCurrency: post.priceCurrency,
    priceUnit: post.priceUnit,
    categories,
    comment: post.comment,
  };

  return (
    <div className="page">
      <h1>{published ? `✅ ${t.success.title}` : t.success.pendingTitle}</h1>
      <p className="muted">{published ? t.success.text : t.success.pendingText}</p>

      <BoardingPassCard data={data} stamped={published} status={t.status[post.status]} />

      {published && post.channelUrl ? (
        <PrimaryButton label={t.success.openChannel} onClick={() => openLink(post.channelUrl!)} />
      ) : null}

      {post.deepLink ? (
        <GhostButton
          label={t.success.share}
          onClick={() => {
            track(EV.POST_SHARE, { target: 'telegram' }, post.id);
            shareUrl(post.deepLink!, t.postType[post.postType]);
          }}
        />
      ) : null}

      <div className={styles.row}>
        <GhostButton label={t.success.newPost} onClick={() => navigate('/new')} />
        <GhostButton label={t.my.title} onClick={() => navigate('/my')} />
      </div>
    </div>
  );
}
