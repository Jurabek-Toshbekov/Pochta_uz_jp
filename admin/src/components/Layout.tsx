import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

/**
 * Boshqaruv minorasi karkasi (§11.3): quyuq navigatsiya + oq kontent.
 */

const LINKS: { to: string; label: string }[] = [
  { to: '/', label: 'Umumiy ko‘rinish' },
  { to: '/posts', label: 'Moderatsiya' },
  { to: '/users', label: 'Foydalanuvchilar' },
  { to: '/messages', label: 'Xabarlar' },
  { to: '/reports', label: 'Shikoyatlar' },
  { to: '/analytics', label: 'Analitika' },
  { to: '/search-insights', label: 'Qidiruv tahlili' },
  { to: '/notifications', label: 'Xabarnomalar' },
  { to: '/settings', label: 'Sozlamalar' },
  { to: '/audit', label: 'Audit' },
];

export function Layout() {
  const { role, logout } = useAuth();
  // Foydalanuvchilar javobi kelganini sezish uchun — har 30 soniyada.
  const unread = useQuery({
    queryKey: ['admin-unread-messages'],
    queryFn: api.unreadMessages,
    refetchInterval: 30_000,
  });
  const unreadCount = unread.data?.count ?? 0;

  return (
    <div className="shell">
      <nav className="nav">
        <div className="nav__brand">Pochta · boshqaruv</div>
        {LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.to === '/'}
            className={({ isActive }) => `nav__link ${isActive ? 'nav__link--active' : ''}`}
          >
            {link.label}
            {link.to === '/messages' && unreadCount > 0 && (
              <span className="nav__count" aria-label={`${unreadCount} ta o‘qilmagan xabar`}>{unreadCount}</span>
            )}
          </NavLink>
        ))}
        <div className="nav__footer">
          <div className="muted">Rol: {role ?? '—'}</div>
          <button
            type="button"
            onClick={logout}
            style={{ marginTop: 8, width: '100%' }}
          >
            Chiqish
          </button>
        </div>
      </nav>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
