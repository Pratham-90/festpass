import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { keys, useQueueStatus } from '../api/queries.js'
import { useStomp } from '../realtime/useStomp.js'
import Countdown from './Countdown.jsx'
import { Button, ButtonLink, Card } from './ui.jsx'
import { useToast } from './Toasts.jsx'
import { useServerNow } from '../lib/serverClock.js'

/**
 * The flash-drop waiting room: join before the sale opens, watch your place in the queue,
 * and get let in (the admission token arrives over WebSocket). Phase 5 turns this into the
 * full animated waiting-room screen.
 *
 * The admission token ends up in the queue status query (keys.queue), where the event page reads it.
 */
export default function WaitingRoomPanel({ event, me }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const now = useServerNow()
  const [counters, setCounters] = useState(null)
  const status = useQueueStatus(event.id, {
    enabled: !!me,
    // Polling is only a fallback for the WebSocket messages below.
    refetchInterval: (query) => (query.state.data?.status === 'WAITING' ? 5000 : false),
  })

  useStomp(`/topic/events/${event.id}/queue`, setCounters)
  useStomp(
    '/user/queue/admission',
    (message) => {
      if (message.eventId !== event.id) return
      queryClient.setQueryData(keys.queue(event.id), (old) => ({
        ...old,
        status: 'ADMITTED',
        position: null,
        admissionToken: message.admissionToken,
        admissionExpiresAt: message.expiresAt,
      }))
      toast.success("You're in!", 'Pick your seats before your 10 minutes are up.')
    },
    { enabled: !!me },
  )

  const join = useMutation({
    mutationFn: () => api(`/events/${event.id}/queue`, { method: 'POST' }),
    onSuccess: (data) => queryClient.setQueryData(keys.queue(event.id), data),
    onError: toast.error,
  })

  const opensAt = Date.parse(event.saleOpensAt)
  const waiting = counters?.waiting ?? status.data?.waiting ?? 0
  const admitted = counters?.admittedSoFar ?? status.data?.admittedSoFar ?? 0
  const state = status.data?.status

  return (
    <Card className="border-accent/40 bg-gradient-to-br from-accent/10 to-transparent">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold tracking-[0.2em] text-accent uppercase">⚡ Flash drop waiting room</p>
          {now < opensAt ? (
            <p className="mt-1 flex flex-wrap items-baseline gap-2">
              Sale opens in <Countdown to={event.saleOpensAt} />
            </p>
          ) : (
            <p className="mt-1 text-success">The drop is live. People are being let in now.</p>
          )}
          <p className="mt-1 text-sm text-ink-muted">
            {waiting.toLocaleString('en-IN')} waiting · {admitted.toLocaleString('en-IN')} let in so far
          </p>
        </div>

        <div className="text-right">
          {!me ? (
            <ButtonLink to={`/login?next=${encodeURIComponent(`/events/${event.slug}`)}`}>Log in to join</ButtonLink>
          ) : state === 'WAITING' ? (
            <div>
              <p className="text-sm text-ink-muted">Your place</p>
              <p className="font-display text-4xl font-black">#{status.data.position.toLocaleString('en-IN')}</p>
            </div>
          ) : state === 'ADMITTED' ? (
            <div>
              <p className="font-display text-2xl font-black text-success">You're in! 🎉</p>
              <p className="flex items-baseline gap-1 text-sm text-ink-muted">
                Time to pick: <Countdown to={status.data.admissionExpiresAt} />
              </p>
            </div>
          ) : (
            <Button onClick={() => join.mutate()} loading={join.isPending}>
              {state === 'EXPIRED' ? 'Join again' : 'Join the queue'}
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}
