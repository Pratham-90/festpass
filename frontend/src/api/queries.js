import { useQuery } from '@tanstack/react-query'
import { api } from './client.js'

// Query keys in one place, so mutations know what to refresh.
export const keys = {
  me: ['me'],
  events: (filters) => ['events', filters],
  event: (slug) => ['event', slug],
  seatmap: (eventId) => ['seatmap', eventId],
  queue: (eventId) => ['queue', eventId],
  order: (orderId) => ['order', orderId],
  tickets: ['tickets'],
  ticket: (ticketId) => ['ticket', ticketId],
  orgEvents: ['org', 'events'],
  orgEvent: (eventId) => ['org', 'event', eventId],
  orgSeatmap: (eventId) => ['org', 'seatmap', eventId],
  gateStaff: (eventId) => ['org', 'gate', eventId],
  users: (q) => ['admin', 'users', q],
}

function params(object) {
  const search = new URLSearchParams()
  Object.entries(object).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') search.set(k, v)
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const useEvents = (filters) =>
  useQuery({ queryKey: keys.events(filters), queryFn: () => api(`/events${params(filters)}`), placeholderData: (prev) => prev })

export const useEvent = (slug) => useQuery({ queryKey: keys.event(slug), queryFn: () => api(`/events/${slug}`) })

export const useSeatMap = (eventId, { enabled = true } = {}) =>
  useQuery({ queryKey: keys.seatmap(eventId), queryFn: () => api(`/events/${eventId}/seatmap`), enabled })

export const useQueueStatus = (eventId, { enabled = true, refetchInterval } = {}) =>
  useQuery({ queryKey: keys.queue(eventId), queryFn: () => api(`/events/${eventId}/queue/me`), enabled, refetchInterval })

export const useOrder = (orderId) => useQuery({ queryKey: keys.order(orderId), queryFn: () => api(`/orders/${orderId}`) })

export const useMyTickets = () => useQuery({ queryKey: keys.tickets, queryFn: () => api('/me/tickets') })

export const useTicket = (ticketId) => useQuery({ queryKey: keys.ticket(ticketId), queryFn: () => api(`/tickets/${ticketId}`) })

export const useOrganizerEvents = () => useQuery({ queryKey: keys.orgEvents, queryFn: () => api('/organizer/events') })

export const useOrganizerEvent = (eventId) =>
  useQuery({ queryKey: keys.orgEvent(eventId), queryFn: () => api(`/organizer/events/${eventId}`) })

export const useOrganizerSeatMap = (eventId, { enabled = true } = {}) =>
  useQuery({ queryKey: keys.orgSeatmap(eventId), queryFn: () => api(`/organizer/events/${eventId}/seatmap`), enabled })

export const useGateStaff = (eventId) =>
  useQuery({ queryKey: keys.gateStaff(eventId), queryFn: () => api(`/organizer/events/${eventId}/gate-staff`) })

export const useUsers = (q) =>
  useQuery({ queryKey: keys.users(q), queryFn: () => api(`/admin/users${params({ q })}`), placeholderData: (prev) => prev })
