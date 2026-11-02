import { Navigate, useLocation } from 'react-router'
import { useMe } from './auth.js'
import { EmptyState, PageLoader } from '../components/ui.jsx'

/** Shows the page only to logged-in users (optionally with one of `roles`); otherwise sends them to log in. */
export default function RequireAuth({ roles, children }) {
  const { data: me, isLoading } = useMe()
  const location = useLocation()

  if (isLoading) return <PageLoader />
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (roles && !roles.includes(me.role)) {
    return <EmptyState title="Not for your account" description="This page is for a different role." />
  }
  return children
}
