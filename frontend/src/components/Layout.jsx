import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { isAdmin, isOrganizer, useLogout, useMe } from '../auth/auth.js'
import { useStompStatus } from '../realtime/useStomp.js'
import { useToast } from './Toasts.jsx'

function navItems(me) {
  const items = [{ to: '/', label: 'Discover', end: true }]
  if (me) items.push({ to: '/tickets', label: 'My tickets' })
  if (isOrganizer(me)) items.push({ to: '/organizer', label: 'Organizer' })
  if (isAdmin(me)) items.push({ to: '/admin/users', label: 'Admin' })
  return items
}

const linkClass = ({ isActive }) =>
  `rounded-lg px-3 py-2 text-sm font-semibold transition ${
    isActive ? 'bg-surface-raised text-ink' : 'text-ink-muted hover:text-ink'
  }`

export default function Layout() {
  const { data: me } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  const toast = useToast()
  const { connected, generation } = useStompStatus()
  const [menuOpen, setMenuOpen] = useState(false)
  const items = navItems(me)

  const onLogout = () =>
    logout.mutate(undefined, {
      onSuccess: () => {
        toast.success('Logged out')
        navigate('/')
      },
    })

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-40 border-b border-line/60 bg-night/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
          <Link to="/" className="font-display text-xl font-black tracking-tight" onClick={() => setMenuOpen(false)}>
            Fest<span className="text-accent">Pass</span>
          </Link>

          <nav className="hidden flex-1 items-center gap-1 md:flex" aria-label="Main">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            {/* Shown only after a dropped connection, not during the first connect. */}
            {!connected && generation > 0 && (
              <span className="hidden items-center gap-1.5 text-xs text-warning sm:flex" title="Reconnecting to live updates">
                <span className="size-2 animate-pulse rounded-full bg-warning" /> Reconnecting
              </span>
            )}
            {me ? (
              <div className="hidden items-center gap-3 md:flex">
                <span className="text-sm text-ink-muted">{me.name}</span>
                <button onClick={onLogout} className="text-sm font-semibold text-ink-muted hover:text-ink">
                  Log out
                </button>
              </div>
            ) : (
              <div className="hidden items-center gap-2 md:flex">
                <NavLink to="/login" className={linkClass}>
                  Log in
                </NavLink>
                <Link to="/register" className="rounded-lg bg-accent px-3 py-2 text-sm font-semibold text-white">
                  Sign up
                </Link>
              </div>
            )}
            <button
              className="flex size-11 items-center justify-center rounded-lg text-xl md:hidden"
              aria-label="Menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? '✕' : '☰'}
            </button>
          </div>
        </div>

        {menuOpen && (
          <nav className="border-t border-line/60 px-4 py-3 md:hidden" aria-label="Main">
            <div className="flex flex-col gap-1" onClick={() => setMenuOpen(false)}>
              {items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                  {item.label}
                </NavLink>
              ))}
              {me ? (
                <button onClick={onLogout} className="rounded-lg px-3 py-2 text-left text-sm font-semibold text-ink-muted">
                  Log out ({me.name})
                </button>
              ) : (
                <>
                  <NavLink to="/login" className={linkClass}>
                    Log in
                  </NavLink>
                  <NavLink to="/register" className={linkClass}>
                    Sign up
                  </NavLink>
                </>
              )}
            </div>
          </nav>
        )}
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-10">
        <Outlet />
      </main>

      <footer className="border-t border-line/60 py-6 text-center text-xs text-ink-muted">
        FestPass · UCS553 Enterprise Web Application, TIET
      </footer>
    </div>
  )
}
