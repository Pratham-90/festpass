import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, animate, motion, useMotionValue, useTransform } from 'framer-motion'
import { api } from '../api/client.js'
import { keys, useQueueStatus } from '../api/queries.js'
import { useStomp } from '../realtime/useStomp.js'
import Countdown from './Countdown.jsx'
import { Button, ButtonLink } from './ui.jsx'
import { useToast } from './Toasts.jsx'
import { celebrate } from '../lib/celebrate.js'
import { useServerNow } from '../lib/serverClock.js'

/** A number that counts smoothly to its new value, e.g. "1,284 people waiting". */
function AnimatedNumber({ value }) {
  const motionValue = useMotionValue(value)
  const text = useTransform(motionValue, (v) => Math.round(v).toLocaleString('en-IN'))
  useEffect(() => {
    const controls = animate(motionValue, value, { duration: 0.6, ease: 'easeOut' })
    return controls.stop
  }, [value, motionValue])
  return <motion.span className="tabular-nums">{text}</motion.span>
}

/**
 * The flash-drop waiting room (PLAN.md section 8, feature 3): a big countdown to the sale,
 * a live count of people waiting, your place in the queue as a moving bar, and a "You're in!"
 * moment when the server admits you (the admission token arrives over WebSocket).
 *
 * The token ends up in the queue status query (keys.queue), where the event page reads it.
 */
export default function WaitingRoomPanel({ event, me, accent }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const now = useServerNow()
  const [counters, setCounters] = useState(null)
  const startPosition = useRef(null)
  const celebrated = useRef(false)

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
    },
    { enabled: !!me },
  )

  const join = useMutation({
    mutationFn: () => api(`/events/${event.id}/queue`, { method: 'POST' }),
    onSuccess: (data) => queryClient.setQueryData(keys.queue(event.id), data),
    onError: toast.error,
  })

  const state = status.data?.status
  const position = status.data?.position ?? null
  const opensAt = Date.parse(event.saleOpensAt)
  const waiting = counters?.waiting ?? status.data?.waiting ?? 0
  const admittedSoFar = counters?.admittedSoFar ?? status.data?.admittedSoFar ?? 0

  // Remember where we started, so the bar shows how far we've moved up.
  if (state === 'WAITING' && position != null && (startPosition.current == null || position > startPosition.current)) {
    startPosition.current = position
  }
  const progress = state === 'WAITING' && startPosition.current
    ? 1 - (position - 1) / startPosition.current
    : 0

  // Confetti once, the moment we're admitted (not on later visits while still admitted).
  const wasWaiting = useRef(false)
  if (state === 'WAITING') wasWaiting.current = true
  useEffect(() => {
    if (state === 'ADMITTED' && wasWaiting.current && !celebrated.current) {
      celebrated.current = true
      celebrate(accent ? [accent] : undefined)
    }
  }, [state, accent])

  return (
    <section className="relative overflow-hidden rounded-card border border-accent/40 bg-gradient-to-br from-accent/15 via-surface to-surface p-5 sm:p-8">
      <p className="text-sm font-semibold tracking-[0.2em] text-accent uppercase">⚡ Flash drop waiting room</p>

      <div className="mt-4 grid gap-6 sm:grid-cols-[1fr_auto] sm:items-end">
        <div>
          {now < opensAt ? (
            <>
              <p className="text-ink-muted">Sale opens in</p>
              <Countdown to={event.saleOpensAt} size="lg" />
            </>
          ) : (
            <p className="font-display text-2xl font-black text-success sm:text-3xl">The drop is live</p>
          )}
          <p className="mt-3 text-lg">
            <span className="font-display text-2xl font-black"><AnimatedNumber value={waiting} /></span>{' '}
            <span className="text-ink-muted">{waiting === 1 ? 'person' : 'people'} waiting</span>
            <span className="text-ink-muted"> · </span>
            <span className="font-semibold"><AnimatedNumber value={admittedSoFar} /></span>{' '}
            <span className="text-ink-muted">let in</span>
          </p>
        </div>

        <div className="sm:min-w-56 sm:text-right">
          <AnimatePresence mode="wait">
            {!me ? (
              <motion.div key="login" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <ButtonLink to={`/login?next=${encodeURIComponent(`/events/${event.slug}`)}`}>Log in to join</ButtonLink>
              </motion.div>
            ) : state === 'WAITING' ? (
              <motion.div key="waiting" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                <p className="text-sm text-ink-muted">Your place in the queue</p>
                <p className="font-display text-5xl font-black">#<AnimatedNumber value={position} /></p>
              </motion.div>
            ) : state === 'ADMITTED' ? (
              <motion.div
                key="admitted"
                initial={{ scale: 0.6, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 260, damping: 12 }}
              >
                <p className="font-display text-4xl font-black text-success">You're in! 🎉</p>
                <p className="mt-1 flex items-baseline gap-1 text-sm text-ink-muted sm:justify-end">
                  Pick your seats within <Countdown to={status.data.admissionExpiresAt} />
                </p>
              </motion.div>
            ) : (
              <motion.div key="join" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <Button onClick={() => join.mutate()} loading={join.isPending} className="w-full sm:w-auto">
                  {state === 'EXPIRED' ? 'Your turn ran out. Join again' : 'Join the queue'}
                </Button>
                <p className="mt-2 text-xs text-ink-muted">People are let in in the order they joined.</p>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {state === 'WAITING' && (
        <div className="mt-6">
          <div
            className="h-3 overflow-hidden rounded-full bg-accent/15"
            role="progressbar"
            aria-label="Progress through the queue"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(progress * 100)}
          >
            <motion.div
              className="h-full rounded-full bg-accent"
              animate={{ width: `${Math.max(3, progress * 100)}%` }}
              transition={{ type: 'spring', stiffness: 60, damping: 18 }}
            />
          </div>
          <p className="mt-2 text-xs text-ink-muted">
            {now < opensAt
              ? 'Stay on this page. When the sale opens, people are let in a batch at a time.'
              : `${Math.max(0, position - 1).toLocaleString('en-IN')} ahead of you. Hang tight, this page updates by itself.`}
          </p>
        </div>
      )}
    </section>
  )
}
