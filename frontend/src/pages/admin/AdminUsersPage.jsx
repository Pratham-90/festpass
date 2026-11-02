import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client.js'
import { useUsers } from '../../api/queries.js'
import { useMe } from '../../auth/auth.js'
import { EmptyState, ErrorState, PageLoader, PageTitle, inputClass } from '../../components/ui.jsx'
import { useToast } from '../../components/Toasts.jsx'
import { formatDate, titleCase } from '../../lib/format.js'

const ROLES = ['STUDENT', 'ORGANIZER', 'GATE_STAFF', 'ADMIN']

export default function AdminUsersPage() {
  const { data: me } = useMe()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [q, setQ] = useState('')
  useEffect(() => {
    const id = setTimeout(() => setQ(query.trim()), 300)
    return () => clearTimeout(id)
  }, [query])
  const users = useUsers(q)

  const changeRole = useMutation({
    mutationFn: ({ id, role }) => api(`/admin/users/${id}/role`, { method: 'PUT', body: { role } }),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      toast.success(`${user.name} is now ${titleCase(user.role)}`, 'It applies from their next login.')
    },
    onError: toast.error,
  })

  return (
    <div className="mx-auto max-w-3xl">
      <PageTitle eyebrow="Admin" title="Users" />
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name or email"
        aria-label="Search users"
        className={`${inputClass} mb-4 max-w-md`}
      />
      {users.isLoading ? (
        <PageLoader />
      ) : users.isError ? (
        <ErrorState error={users.error} onRetry={users.refetch} />
      ) : users.data.length === 0 ? (
        <EmptyState title="No users found" description="Try another search." />
      ) : (
        <ul className="flex flex-col divide-y divide-line rounded-card border border-line bg-surface">
          {users.data.map((u) => (
            <li key={u.id} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{u.name}{u.id === me?.id && <span className="text-ink-muted"> (you)</span>}</p>
                <p className="truncate text-sm text-ink-muted">{u.email} · joined {formatDate(u.createdAt)}</p>
              </div>
              <select
                aria-label={`Role for ${u.name}`}
                className={`${inputClass} w-40`}
                value={u.role}
                disabled={u.id === me?.id || changeRole.isPending}
                title={u.id === me?.id ? "You can't change your own role" : undefined}
                onChange={(e) => changeRole.mutate({ id: u.id, role: e.target.value })}
              >
                {ROLES.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
              </select>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
