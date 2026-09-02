import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Subscription } from '../api/types';
import {
  EmptyState,
  ErrorState,
  GhostButton,
  Loader,
  uiStyles as styles,
} from '../components/primitives';
import { useDeleteSubscription, useSubscriptions } from '../hooks/useSearchPosts';
import { useBackButton } from '../hooks/useTelegram';
import { useT } from '../i18n/useT';

/** Xabarnoma obunalari (§9.1 {@code /subscriptions}). */
export function SubscriptionsPage() {
  const t = useT();
  const navigate = useNavigate();
  const subscriptions = useSubscriptions();
  const remove = useDeleteSubscription();

  useBackButton(useCallback(() => navigate('/'), [navigate]));

  if (subscriptions.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (subscriptions.isError) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void subscriptions.refetch()}
        />
      </div>
    );
  }

  const list = subscriptions.data ?? [];

  return (
    <div className="page">
      <h1>{t.subs.title}</h1>

      {list.length === 0 ? (
        <EmptyState
          message={t.subs.empty}
          actionLabel={t.search.title}
          onAction={() => navigate('/search')}
        />
      ) : (
        list.map((subscription) => (
          <div key={subscription.id} className={styles.card}>
            <div className={styles.row}>
              <span className="iata">{describe(subscription, t.subs.any)}</span>
            </div>
            <p className={styles.hint}>
              {subscription.postType ? t.postType[subscription.postType] : t.search.anyType}
              {subscription.dateFrom ? ` · ${subscription.dateFrom}` : ''}
              {subscription.dateTo ? ` — ${subscription.dateTo}` : ''}
            </p>
            <GhostButton
              label={t.subs.delete}
              onClick={() => remove.mutate(subscription.id)}
            />
          </div>
        ))
      )}
    </div>
  );
}

function describe(subscription: Subscription, anyLabel: string): string {
  const origin = subscription.originAirport ?? anyLabel;
  const dest = subscription.destAirport ?? anyLabel;
  return `${origin} → ${dest}`;
}
