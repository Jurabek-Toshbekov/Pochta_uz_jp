import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { PostSummary } from '../api/types';
import { EV } from '../analytics/events';
import { track } from '../analytics/track';
import { BoardingPassCard } from '../components/BoardingPassCard';
import { SearchFilters } from '../components/SearchFilters';
import {
  EmptyState,
  ErrorState,
  GhostButton,
  Loader,
  Notice,
  PrimaryButton,
  uiStyles as styles,
} from '../components/primitives';
import { useReference } from '../hooks/useReference';
import { useCreateSubscription, useSearchPosts } from '../hooks/useSearchPosts';
import { useBackButton, haptic } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';
import { toBoardingPassData } from '../lib/boardingPass';
import { useLanguage } from '../store/appStore';
import {
  activeFilterCount,
  canSubscribe,
  toSearchParams,
  toSubscriptionInput,
  useSearchStore,
} from '../store/searchStore';

/**
 * Qidiruv ekrani (§10.1).
 *
 * Natijasiz qidiruv bo'sh ekran bilan tugamaydi: "chiqsa xabar beraymi?"
 * taklifi ko'rsatiladi (§6.4, 4-band). Bu foydalanuvchiga foyda, bizga esa
 * qoplanmagan talab haqidagi ma'lumot.
 */
export function SearchPage() {
  const t = useT();
  const navigate = useNavigate();
  const reference = useReference();
  const filters = useSearchStore();
  const resetFilters = useSearchStore((state) => state.reset);
  const language = useLanguage();

  const [filtersOpen, setFiltersOpen] = useState(false);
  const [subscribed, setSubscribed] = useState(false);

  const params = useMemo(() => toSearchParams(filters), [filters]);
  const query = useSearchPosts(params);
  const createSubscription = useCreateSubscription();

  useBackButton(useCallback(() => navigate('/'), [navigate]));

  useEffect(() => {
    track(EV.SEARCH_OPEN, { entry_point: 'route' });
  }, []);

  const items: PostSummary[] = useMemo(
    () => (query.data?.pages ?? []).flatMap((page) => page.items),
    [query.data],
  );
  const totalCount = query.data?.pages?.[0]?.totalCount;
  const filterCount = activeFilterCount(filters);

  const openPost = (post: PostSummary, position: number) => {
    haptic();
    track(EV.SEARCH_RESULT_CLICK, { position, result_count: items.length }, post.id);
    navigate(`/post/${post.id}`);
  };

  const subscribe = () => {
    if (!canSubscribe(filters)) {
      return;
    }
    createSubscription.mutate(toSubscriptionInput(filters), {
      onSuccess: () => setSubscribed(true),
    });
  };

  if (reference.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  return (
    <div className="page">
      <div className={styles.row}>
        <h1>{t.search.title}</h1>
      </div>

      <div className={`${styles.row} ${styles.rowFill}`}>
        <GhostButton
          label={filterCount > 0 ? `${t.search.filters} (${filterCount})` : t.search.filters}
          onClick={() => setFiltersOpen((open) => !open)}
        />
        {filterCount > 0 ? (
          <GhostButton
            label={t.search.reset}
            onClick={() => {
              resetFilters();
              setSubscribed(false);
            }}
          />
        ) : null}
      </div>

      {filtersOpen ? (
        <div className={styles.card}>
          <SearchFilters
            airports={reference.data?.airports ?? []}
            categories={reference.data?.categories ?? []}
          />
          <PrimaryButton label={t.search.apply} onClick={() => setFiltersOpen(false)} />
        </div>
      ) : null}

      {query.isError ? (
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void query.refetch()}
        />
      ) : null}

      {query.isLoading ? <Loader text={t.common.loading} /> : null}

      {!query.isLoading && !query.isError && items.length === 0 ? (
        <>
          <EmptyState message={`${t.search.noResults} ${t.search.noResultsHint}`} />
          {subscribed ? (
            <Notice>{t.search.subscribed}</Notice>
          ) : canSubscribe(filters) ? (
            <PrimaryButton
              label={t.search.subscribeCta}
              onClick={subscribe}
              disabled={createSubscription.isPending}
            />
          ) : null}
          <GhostButton
            label={t.home.newPost}
            onClick={() => navigate('/new', { state: { entryPoint: 'search_empty' } })}
          />
        </>
      ) : null}

      {items.length > 0 ? (
        <>
          <p className="muted">
            {totalCount ?? items.length} {t.search.results}
          </p>

          {items.map((post, index) => (
            <button
              key={post.id}
              type="button"
              className={styles.resultButton}
              onClick={() => openPost(post, index + 1)}
            >
              <BoardingPassCard
                data={toBoardingPassData(post, reference.data?.airports, reference.data?.categories, language)}
                status={post.verified ? '✅' : undefined}
              />
            </button>
          ))}

          {query.hasNextPage ? (
            <GhostButton
              label={query.isFetchingNextPage ? t.common.loading : t.search.loadMore}
              onClick={() => void query.fetchNextPage()}
            />
          ) : null}

          {subscribed ? (
            <Notice>{t.search.subscribed}</Notice>
          ) : canSubscribe(filters) ? (
            <GhostButton label={t.search.saveSearch} onClick={subscribe} />
          ) : null}
        </>
      ) : null}
    </div>
  );
}
