import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { EventCount, EventRow, OwnPost, SearchRow, ViewedPost, Viewer } from '../api/types';
import { formatDate, formatDateTime, formatNumber } from '../lib/format';
import { EmptyState, StatusBadge } from './ui';

/**
 * Foydalanuvchi profilidagi jadvallar. Har bir odam nomi uning profiliga,
 * har bir e'lon esa Moderatsiya sahifasidagi o'sha e'longa olib boradi.
 */

function PersonLink({ id, username, displayName }: { id: string; username: string | null; displayName: string | null }) {
  return <Link to={`/users/${id}`}>{username ? `@${username}` : displayName ?? 'Profil'}</Link>;
}

function PostLink({ id, origin, dest }: { id: string; origin: string | null; dest: string | null }) {
  return (
    <Link to={`/posts?search=${id}`} className="iata">
      {origin ?? '—'} → {dest ?? '—'}
    </Link>
  );
}

function typeLabel(type: string | null): string {
  if (type === 'CARRY') return 'Olib ketadi';
  if (type === 'SEND') return 'Yuboradi';
  return '—';
}

function Table({ head, empty, rows, scroll }: { head: string[]; empty: string; rows: ReactNode[]; scroll?: boolean }) {
  return (
    <div className="table-wrap" style={scroll ? { maxHeight: 420, overflowY: 'auto' } : undefined}>
      <table className="data">
        <thead>
          <tr>{head.map((title) => <th key={title}>{title}</th>)}</tr>
        </thead>
        <tbody>{rows}</tbody>
      </table>
      {rows.length === 0 && <EmptyState text={empty} />}
    </div>
  );
}

export function OwnPostsTable({ posts }: { posts: OwnPost[] }) {
  return (
    <Table
      head={['Yaratilgan', 'Holat', 'Tur', 'Yo‘nalish', 'Sana', 'Ko‘rish', 'Unikal', 'Kontakt']}
      empty="E'lon bermagan."
      rows={posts.map((post) => (
        <tr key={post.id}>
          <td className="mono">{formatDate(post.createdAt)}</td>
          <td><StatusBadge status={post.status} /></td>
          <td>{typeLabel(post.postType)}</td>
          <td><PostLink id={post.id} origin={post.originAirport} dest={post.destAirport} /></td>
          <td className="mono">{formatDate(post.travelDate)}</td>
          <td className="num">{formatNumber(post.viewCount)}</td>
          <td className="num">{formatNumber(post.uniqueViewers)}</td>
          <td className="num">{formatNumber(post.contactRevealCount)}</td>
        </tr>
      ))}
    />
  );
}

export function ViewedPostsTable({ posts }: { posts: ViewedPost[] }) {
  return (
    <Table
      scroll
      head={['Oxirgi marta', 'E‘lon', 'Tur', 'Egasi', 'Ko‘rgan', 'Kontakt']}
      empty="Boshqalarning e'lonlarini ochmagan."
      rows={posts.map((post) => (
        <tr key={post.postId}>
          <td className="mono">{formatDateTime(post.lastViewedAt)}</td>
          <td><PostLink id={post.postId} origin={post.originAirport} dest={post.destAirport} /></td>
          <td>{typeLabel(post.postType)}</td>
          <td><PersonLink id={post.ownerId} username={post.ownerUsername} displayName={post.ownerDisplayName} /></td>
          <td className="num">{formatNumber(post.views)}</td>
          <td>{post.contactRevealed ? 'Ochgan' : '—'}</td>
        </tr>
      ))}
    />
  );
}

export function ViewersTable({ viewers }: { viewers: Viewer[] }) {
  return (
    <Table
      scroll
      head={['Oxirgi marta', 'Kim', 'E‘lonlari', 'Ko‘rish', 'Kontakt']}
      empty="E'lonlarini hali hech kim ochmagan."
      rows={viewers.map((viewer) => (
        <tr key={viewer.userId}>
          <td className="mono">{formatDateTime(viewer.lastViewedAt)}</td>
          <td><PersonLink id={viewer.userId} username={viewer.username} displayName={viewer.displayName} /></td>
          <td className="num">{formatNumber(viewer.postsViewed)}</td>
          <td className="num">{formatNumber(viewer.views)}</td>
          <td className="num">{viewer.contactReveals > 0 ? formatNumber(viewer.contactReveals) : '—'}</td>
        </tr>
      ))}
    />
  );
}

export function SearchesTable({ searches }: { searches: SearchRow[] }) {
  return (
    <Table
      scroll
      head={['Vaqt', 'Tur', 'Yo‘nalish', 'Sana oralig‘i', 'Matn', 'Natija']}
      empty="Qidiruv qilmagan."
      rows={searches.map((search, index) => (
        <tr key={`${search.createdAt}-${index}`}>
          <td className="mono">{formatDateTime(search.createdAt)}</td>
          <td>{typeLabel(search.postType)}</td>
          <td className="iata">
            {search.originAirport || search.destAirport
              ? `${search.originAirport ?? '*'} → ${search.destAirport ?? '*'}`
              : search.direction ?? '—'}
          </td>
          <td className="mono">
            {search.dateFrom || search.dateTo ? `${formatDate(search.dateFrom)} — ${formatDate(search.dateTo)}` : '—'}
          </td>
          <td>{search.textQuery ?? '—'}</td>
          <td className={search.resultCount === 0 ? 'num error' : 'num'}>{formatNumber(search.resultCount)}</td>
        </tr>
      ))}
    />
  );
}

export function EventBreakdownTable({ events }: { events: EventCount[] }) {
  return (
    <Table
      scroll
      head={['Event', 'Soni', 'Oxirgi marta']}
      empty="Hali harakat yozilmagan."
      rows={events.map((event) => (
        <tr key={event.eventName}>
          <td className="mono">{event.eventName}</td>
          <td className="num">{formatNumber(event.count)}</td>
          <td className="mono">{formatDateTime(event.lastAt)}</td>
        </tr>
      ))}
    />
  );
}

/** Properties qisqa ko'rinishda: kalit=qiymat, uzun qiymatlar kesiladi. */
function compactProperties(properties: Record<string, unknown>): string {
  return Object.entries(properties)
    .map(([key, value]) => {
      const text = typeof value === 'string' ? value : JSON.stringify(value);
      return `${key}=${text.length > 40 ? `${text.slice(0, 40)}…` : text}`;
    })
    .join(' · ');
}

export function TimelineTable({ events }: { events: EventRow[] }) {
  return (
    <Table
      scroll
      head={['Vaqt', 'Event', 'Manba', 'Tafsilot']}
      empty="Hali harakat yozilmagan."
      rows={events.map((event, index) => (
        <tr key={`${event.occurredAt}-${index}`}>
          <td className="mono">{formatDateTime(event.occurredAt)}</td>
          <td className="mono">{event.eventName}</td>
          <td className="muted">{event.source}</td>
          <td className="muted" style={{ fontSize: 12 }}>{compactProperties(event.properties)}</td>
        </tr>
      ))}
    />
  );
}
