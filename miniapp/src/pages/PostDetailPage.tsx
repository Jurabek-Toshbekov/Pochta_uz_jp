import { useCallback, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import { BoardingPassCard } from '../components/BoardingPassCard';
import {
  ErrorState,
  GhostButton,
  Loader,
  Notice,
  PrimaryButton,
  uiStyles as styles,
} from '../components/primitives';
import { useReference } from '../hooks/useReference';
import { usePostDetail, useRevealContact } from '../hooks/useSearchPosts';
import { openLink, shareUrl, useBackButton, useMainButton, hapticSuccess } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { toBoardingPassData } from '../lib/boardingPass';
import { useLanguage } from '../store/appStore';

/**
 * E'lon tafsiloti (§9.1 {@code /post/:id}).
 *
 * Kontakt darhol ko'rinmaydi — "Bog'lanish" bosilganda ochiladi (§6.4, 2-band).
 * Bu qulaylikni yo'qotmaydi: bir marta bosish qo'shiladi, lekin biz kim
 * kim bilan bog'langanini bilamiz.
 */
export function PostDetailPage() {
  const t = useT();
  const language = useLanguage();
  const navigate = useNavigate();
  const { postId } = useParams();
  const reference = useReference();
  const detail = usePostDetail(postId);
  const reveal = useRevealContact(postId);

  const openedAt = useRef<number>(Date.now());

  useBackButton(useCallback(() => navigate(-1), [navigate]));

  useEffect(() => {
    if (detail.data) {
      track(EV.POST_DETAIL_VIEW, { source: 'search' }, detail.data.post.id);
    }
  }, [detail.data]);

  // Ekranda qancha turgani — qiymat signalining bir qismi (§6.1).
  useEffect(
    () => () => {
      if (postId) {
        track(EV.POST_VIEW, { time_on_screen_ms: Date.now() - openedAt.current }, postId);
      }
    },
    [postId],
  );

  const contact = reveal.data;
  const revealed = Boolean(contact) || detail.data?.contactRevealed;

  const onReveal = () => {
    reveal.mutate(undefined, {
      onSuccess: () => hapticSuccess(),
    });
  };

  useMainButton({
    text: revealed ? t.common.back : t.detail.reveal,
    onClick: revealed ? () => navigate(-1) : onReveal,
    enabled: !reveal.isPending,
    loading: reveal.isPending,
  });

  if (detail.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (detail.isError || !detail.data) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorGeneric}
          retryLabel={t.common.retry}
          onRetry={() => void detail.refetch()}
        />
        <GhostButton label={t.search.title} onClick={() => navigate('/search')} />
      </div>
    );
  }

  const { post, own, deepLink, channelUrl } = detail.data;

  return (
    <div className="page">
      <BoardingPassCard
        data={toBoardingPassData(post, reference.data?.airports, reference.data?.categories, language)}
        status={post.verified ? t.detail.verified : undefined}
      />

      <p className="muted">
        {post.viewCount} {t.detail.views} · {post.contactRevealCount} {t.detail.reveals}
      </p>

      {own ? <Notice>{t.detail.ownPost}</Notice> : null}

      <div className={styles.card}>
        <span className={styles.label}>{t.detail.contact}</span>

        {contact ? (
          <div className={styles.section}>
            {contact.telegram ? (
              <PrimaryButton
                label={`${t.detail.openTelegram}: @${contact.telegram}`}
                onClick={() => {
                  track(EV.CONTACT_CLICK, { method: 'telegram' }, post.id);
                  openLink(`https://t.me/${contact.telegram}`);
                }}
              />
            ) : null}
            {contact.phone ? (
              <GhostButton
                label={`${t.detail.call}: ${contact.phone}`}
                onClick={() => {
                  track(EV.CONTACT_CLICK, { method: 'phone' }, post.id);
                  window.location.href = `tel:${contact.phone}`;
                }}
              />
            ) : null}
            {contact.other ? <p>{contact.other}</p> : null}
          </div>
        ) : (
          <>
            <p className={styles.hint}>{t.detail.revealHint}</p>
            <PrimaryButton
              label={reveal.isPending ? t.common.loading : t.detail.reveal}
              onClick={onReveal}
              disabled={reveal.isPending}
            />
            {reveal.isError ? (
              <p className={styles.error} role="alert">
                {t.common.errorGeneric}
              </p>
            ) : null}
          </>
        )}
      </div>

      {channelUrl ? (
        <GhostButton label={t.detail.openChannel} onClick={() => openLink(channelUrl)} />
      ) : null}

      {deepLink ? (
        <GhostButton
          label={t.detail.share}
          onClick={() => {
            track(EV.POST_SHARE, { target: 'telegram' }, post.id);
            shareUrl(deepLink, t.postType[post.postType]);
          }}
        />
      ) : null}

      {/* Shikoyat oqimi 5-bosqichda ishga tushadi (§13). Tugma hozircha
          ochiq aytadi — yashirilmaydi, chunki har bir e'londa bo'lishi
          §7.3 talabi. */}
      <GhostButton label={t.detail.report} onClick={() => window.alert(t.detail.reportSoon)} />
    </div>
  );
}
