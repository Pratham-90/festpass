import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/client.js'
import { keys } from '../api/queries.js'
import { reconnectStomp } from '../realtime/useStomp.js'

/** The logged-in user ({ id, name, email, role }), or null for visitors. */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async () => {
      try {
        return await api('/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
  })
}

function useAfterAuthChange() {
  const queryClient = useQueryClient()
  return async (user) => {
    // Drop everything cached for the previous user, then reconnect the WebSocket as the new one.
    queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'events' && q.queryKey[0] !== 'event' })
    queryClient.setQueryData(keys.me, user)
    await reconnectStomp()
  }
}

export function useLogin() {
  const afterAuthChange = useAfterAuthChange()
  return useMutation({
    mutationFn: (credentials) => api('/auth/login', { method: 'POST', body: credentials }),
    onSuccess: afterAuthChange,
  })
}

export function useRegister() {
  const afterAuthChange = useAfterAuthChange()
  return useMutation({
    mutationFn: (details) => api('/auth/register', { method: 'POST', body: details }),
    onSuccess: afterAuthChange,
  })
}

export function useLogout() {
  const afterAuthChange = useAfterAuthChange()
  return useMutation({
    mutationFn: () => api('/auth/logout', { method: 'POST' }),
    onSettled: () => afterAuthChange(null),
  })
}

export const isOrganizer = (user) => user?.role === 'ORGANIZER' || user?.role === 'ADMIN'
export const isAdmin = (user) => user?.role === 'ADMIN'
