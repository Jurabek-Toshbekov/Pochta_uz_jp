import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { PostFilters } from '../api/types';
import { PostDetailCard } from '../components/PostDetailCard';
import { DEFAULT_POST_FILTERS, PostFiltersBar } from '../components/PostFiltersBar';
import { EmptyState, ErrorState, Loader, Pager, RiskBadge, StatusBadge } from '../components/ui';
import { formatDate, formatMoney, formatNumber } from '../lib/format';

/**
 * Moderatsiya (§11.2 — /posts).
 *
 * <p>Navbat tartibi serverda: {@code PENDING} va yuqori riskli kategoriyalar
 * tepada. Har bir harakat sabab bilan yoziladi va jurnalga tushadi.
 */

/** Filtrlar URL'da: bo'sh qiymat kalit bilan birga olib tashlanadi. */
function readFilters(params: URLSearchParams): PostFilters {
  const filters = { ...DEFAULT_POST_FILTERS };
  for (const key of Object.keys(filters) as (keyof PostFilters)[]) {
    const value = params.get(key);
    if (value !== null) {
      filters[key] = value;
    }
  }
  return filters;
}

export function PostsPage() {
  const [params, setParams] = useSearchParams();
  const filters = readFilters(params);
  const page = Number(params.get('page') ?? '0') || 0;
  const [selected, setSelected] = useState<string | null>(null);

  const update = (patch: Partial<PostFilters>, nextPage = 0) => {
    const next = { ...filters, ...patch };
    const result = new URLSearchParams();
    for (const [key, value] of Object.entries(next)) {
      // Sukut qiymatidan farqli bo'lsa yoziladi (status uchun bo'sh = "Barchasi").
      if (value !== DEFAULT_POST_FILTERS[key as keyof PostFilters]) {
        result.set(key, value);
      }
    }
    if (nextPage > 0) {
      result.set('page', String(nextPage));
    }
    setParams(result, { replace: true });
  };

  const size = 25;
  const list = useQuery({
    queryKey: ['admin-posts', filters, page],
    queryFn: () => api.posts({ ...filters, page, size }),
  });

  return (
    <>
      <div className="page-head">
        <h1>Moderatsiya</h1>
        <span className="muted">Kutayotgan va yuqori riskli e'lonlar tepada</span>
      </div>

      <PostFiltersBar
        filters={filters}
        onChange={(patch) => update(patch)}
        onReset={() => setParams(new URLSearchParams(), { replace: true })}
      />

      {list.isLoading && <Loader />}
      {list.isError && <ErrorState message="Ro‘yxat yuklanmadi." onRetry={() => void list.refetch()} />}

      {list.data && (
        <>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Sana</th>
                  <th>Holat</th>
                  <th>Tur</th>
                  <th>Yo‘nalish</th>
                  <th>Sana (uchish)</th>
                  <th>Kategoriya</th>
                  <th className="num">Narx</th>
                  <th>Foydalanuvchi</th>
                  <th className="num">Ko‘rish</th>
                  <th className="num">Kontakt</th>
                  <th className="num">Shikoyat</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((row) => (
                  <tr key={row.id}>
                    <td className="mono">{formatDate(row.createdAt)}</td>
                    <td><StatusBadge status={row.status} /></td>
                    <td>{row.postType === 'CARRY' ? 'Olib ketadi' : 'Yuboradi'}</td>
                    <td className="iata">
                      {row.originAirport ?? '—'} → {row.destAirport ?? '—'}
                    </td>
                    <td className="mono">{formatDate(row.departDate ?? row.deadlineDate)}</td>
                    <td>
                      {row.riskLevel === 'HIGH' ? <RiskBadge level={row.riskLevel} /> : null}{' '}
                      <span className="muted">{row.categories.join(', ') || '—'}</span>
                    </td>
                    <td className="num">{formatMoney(row.priceAmount, row.priceCurrency)}</td>
                    <td>
                      <Link to={`/users/${row.userId}`}>
                        {row.username ? `@${row.username}` : row.userDisplayName ?? '—'}
                      </Link>
                    </td>
                    <td className="num">{formatNumber(row.viewCount)}</td>
                    <td className="num">{formatNumber(row.contactRevealCount)}</td>
                    <td className="num">{row.reportCount > 0 ? formatNumber(row.reportCount) : '—'}</td>
                    <td>
                      <button type="button" onClick={() => setSelected(row.id)}>
                        Ochish
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.data.items.length === 0 && <EmptyState text="Bu filtrda e'lon yo‘q." />}
          </div>
          <Pager page={page} size={size} total={list.data.total} onChange={(next) => update({}, next)} />
        </>
      )}

      {selected && <PostDetailCard postId={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
