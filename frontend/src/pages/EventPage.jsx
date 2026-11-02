import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { keys, useEvent, useQueueStatus, useSeatMap } from '../api/queries.js'
import { useMe } from '../auth/auth.js'
import { useStomp, useStompStatus } from '../realtime/useStomp.js'
import { EventBanner } from '../components/EventCard.jsx'
import SeatMap, { SeatLegend } from '../components/SeatMap.jsx'
import Countdown from '../components/Countdown.jsx'
import WaitingRoomPanel from '../components/WaitingRoomPanel.jsx'
import { Badge, Button, Card, ErrorState, PageLoader } from '../components/ui.jsx'
import { useToast } from '../components/Toasts.jsx'
import { formatDateTime, formatMoney, formatTime, titleCase } from '../lib/format.js'
import { newKey } from '../lib/ids.js'
import { useServerNow } from '../lib/serverClock.js'

export default function EventPage() {
  const { slug } = useParams()
  const event = useEvent(slug)
  if (event.isLoading) return <PageLoader />
  if (event.isError) return <ErrorState error={event.error} onRetry={event.error.status === 404 ? undefined : event.refetch} />
  return <EventView event={event.data} />
}

function EventView({ event }) {
  const { data: me } = useMe()
  const now = useServerNow()
  const queryClient = useQueryClient()
  const { generation } = useStompStatus()
  const [stats, setStats] = useState(null)

  const seated = event.seatingMode === 'SEATED'
  const saleOpen = now >= Date.parse(event.saleOpensAt) && now < Date.parse(event.saleClosesAt)
  const queue = useQueueStatus(event.id, { enabled: event.flashDrop && !!me })
  const admissionToken = queue.data?.status === 'ADMITTED' ? queue.data.admissionToken : null
  const canBook = !!me && saleOpen && (!event.flashDrop || !!admissionToken)

  // Live: viewers and remaining counts.
  useStomp(`/topic/events/${event.id}/stats`, setStats)

  // After a dropped connection we may have missed updates, so reload the seat map and counts.
  useEffect(() => {
    if (generation > 1) {
      queryClient.invalidateQueries({ queryKey: keys.seatmap(event.id) })
      queryClient.invalidateQueries({ queryKey: keys.event(event.slug) })
    }
  }, [generation, event.id, event.slug, queryClient])

  const tiers = event.tiers.map((t) => ({ ...t, remaining: stats?.remainingByTier?.[t.id] ?? t.remaining }))

  return (
    // The event's own accent colour (its first tier) replaces the default one on this page.
    <div style={event.accentColor ? { '--color-accent': event.accentColor } : undefined} className="pb-28">
      <div className="relative -mx-4 -mt-6 mb-6 overflow-hidden sm:mx-0 sm:mt-0 sm:rounded-card">
        <EventBanner event={event} className="h-44 w-full sm:h-64" />
        <div className="absolute inset-0 bg-gradient-to-t from-night via-night/40 to-transparent" />
        <div className="absolute inset-x-0 bottom-0 p-4 sm:p-6">
          <div className="flex flex-wrap gap-2">
            <Badge>{titleCase(event.category)}</Badge>
            {event.flashDrop && <Badge tone="accent">⚡ Flash drop</Badge>}
            {stats?.viewers > 0 && (
              <Badge tone="success">
                <span className="size-1.5 animate-pulse rounded-full bg-success" /> {stats.viewers} viewing now
              </Badge>
            )}
          </div>
          <h1 className="font-display mt-2 text-3xl font-black tracking-tight sm:text-5xl">{event.title}</h1>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex min-w-0 flex-col gap-6">
          <SaleBanner event={event} now={now} />
          {event.flashDrop && <WaitingRoomPanel event={event} me={me} />}
          {seated ? (
            <SeatPicker event={event} tiers={tiers} me={me} canBook={canBook} admissionToken={admissionToken} />
          ) : (
            <GaPicker event={event} tiers={tiers} me={me} canBook={canBook} admissionToken={admissionToken} />
          )}
        </div>

        <aside className="flex flex-col gap-4">
          <Card>
            <dl className="flex flex-col gap-3 text-sm">
              <div>
                <dt className="text-ink-muted">When</dt>
                <dd className="font-semibold">{formatDateTime(event.startsAt)} – {formatTime(event.endsAt)}</dd>
              </div>
              <div>
                <dt className="text-ink-muted">Where</dt>
                <dd className="font-semibold">{event.venueName}</dd>
              </div>
              {event.organizerName && (
                <div>
                  <dt className="text-ink-muted">Organised by</dt>
                  <dd className="font-semibold">{event.organizerName}</dd>
                </div>
              )}
              <div>
                <dt className="text-ink-muted">Limit</dt>
                <dd className="font-semibold">{event.maxTicketsPerUser} tickets per person</dd>
              </div>
            </dl>
          </Card>
          <TierList tiers={tiers} />
          {event.description && (
            <Card>
              <p className="text-sm leading-relaxed whitespace-pre-line text-ink-muted">{event.description}</p>
            </Card>
          )}
        </aside>
      </div>
    </div>
  )
}

function SaleBanner({ event, now }) {
  const opens = Date.parse(event.saleOpensAt)
  const closes = Date.parse(event.saleClosesAt)
  if (event.flashDrop && now < closes) return null // the waiting room shows its own countdown
  if (now < opens) {
    return (
      <Card className="flex flex-wrap items-center justify-between gap-3">
        <span className="font-semibold">Tickets go on sale in</span>
        <Countdown to={event.saleOpensAt} />
      </Card>
    )
  }
  if (now >= closes) {
    return <Card className="text-ink-muted">Ticket sales for this event have closed.</Card>
  }
  return null
}

function TierList({ tiers }) {
  return (
    <Card>
      <h2 className="mb-3 font-semibold">Tickets</h2>
      <ul className="flex flex-col gap-3">
        {tiers.map((t) => (
          <li key={t.id} className="flex items-center gap-3">
            <span className="size-3 shrink-0 rounded" style={{ background: t.colorHex }} />
            <div className="min-w-0 flex-1">
              <p className="font-semibold">{t.name}</p>
              <p className="text-xs text-ink-muted">
                {t.remaining > 0 ? `${t.remaining} of ${t.capacity} left` : 'Sold out'}
              </p>
            </div>
            <span className="font-semibold">{formatMoney(t.pricePaise)}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

/** Hold the tickets, create the order straight away, and go to checkout. */
function useBooking(event, admissionToken, { onSeatsTaken } = {}) {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request) => {
      const hold = await api(`/events/${event.id}/holds`, {
        method: 'POST',
        body: request,
        headers: admissionToken ? { 'X-Admission-Token': admissionToken } : {},
      })
      try {
        return await api('/orders', { method: 'POST', body: { holdId: hold.holdId }, headers: { 'Idempotency-Key': newKey() } })
      } catch (error) {
        api(`/holds/${hold.holdId}`, { method: 'DELETE' }).catch(() => {}) // don't leave the seats stuck
        throw error
      }
    },
    onSuccess: (order) => navigate(`/checkout/${order.id}`),
    onError: (error) => {
      toast.error(error)
      if (error.code === 'SEAT_UNAVAILABLE') onSeatsTaken?.(error.problem.unavailableSeatIds ?? [])
      queryClient.invalidateQueries({ queryKey: keys.seatmap(event.id) })
    },
  })
}

function LoginToBook({ event }) {
  const navigate = useNavigate()
  return (
    <Button onClick={() => navigate(`/login?next=${encodeURIComponent(`/events/${event.slug}`)}`)}>Log in to book</Button>
  )
}

function SeatPicker({ event, tiers, me, canBook, admissionToken }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const seatmap = useSeatMap(event.id)
  const [selected, setSelected] = useState(() => new Set())
  const [shaking, setShaking] = useState(() => new Set())

  const shake = useCallback((ids) => {
    setShaking(new Set(ids))
    setTimeout(() => setShaking(new Set()), 450)
  }, [])

  const dropFromSelection = useCallback(
    (ids) => {
      setSelected((current) => {
        const next = new Set(current)
        ids.forEach((id) => next.delete(id))
        return next
      })
      shake(ids)
    },
    [shake],
  )

  const book = useBooking(event, admissionToken, { onSeatsTaken: dropFromSelection })

  // Live seat changes. Our own hold's update can arrive before the hold request returns,
  // so seats we're holding right now don't count as grabbed by someone else.
  useStomp(`/topic/events/${event.id}/seats`, (deltas) => {
    const statusById = new Map(deltas.map((d) => [d.seatId, d.status]))
    queryClient.setQueryData(keys.seatmap(event.id), (old) =>
      old && { ...old, seats: old.seats.map((s) => (statusById.has(s.id) ? { ...s, status: statusById.get(s.id) } : s)) },
    )
    const holdingNow = new Set(book.isPending ? book.variables?.seatIds : [])
    const grabbed = [...selected].filter(
      (id) => statusById.has(id) && statusById.get(id) !== 'AVAILABLE' && !holdingNow.has(id),
    )
    if (grabbed.length) {
      dropFromSelection(grabbed)
      toast.push({ title: 'Someone just grabbed that seat!', description: 'Pick another one, they go fast.', tone: 'error' })
    }
  })

  const tierById = useMemo(() => new Map(tiers.map((t) => [t.id, t])), [tiers])
  if (seatmap.isLoading) return <PageLoader />
  if (seatmap.isError) return <ErrorState error={seatmap.error} onRetry={seatmap.refetch} />

  const seats = seatmap.data.seats
  const picked = seats.filter((s) => selected.has(s.id))
  const total = picked.reduce((sum, s) => sum + (tierById.get(s.tierId)?.pricePaise ?? 0), 0)

  const toggle = (seat) => {
    if (!canBook) {
      if (!me) toast.push({ title: 'Log in to pick seats' })
      return
    }
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(seat.id)) next.delete(seat.id)
      else if (next.size >= event.maxTicketsPerUser) {
        toast.push({ title: `Up to ${event.maxTicketsPerUser} seats per person` })
      } else next.add(seat.id)
      return next
    })
  }

  return (
    <div>
      <SeatMap
        seats={seats}
        sections={seatmap.data.sections}
        bounds={seatmap.data.bounds}
        selected={selected}
        shaking={shaking}
        onToggle={toggle}
      />
      <SeatLegend tiers={tiers} />

      <ActionBar>
        <div className="min-w-0">
          {picked.length ? (
            <>
              <p className="truncate font-semibold">
                {picked.length} seat{picked.length > 1 ? 's' : ''} · {formatMoney(total)}
              </p>
              <p className="truncate text-xs text-ink-muted">
                {picked.map((s) => `${s.row}${s.number}`).join(', ')}
              </p>
            </>
          ) : (
            <p className="text-sm text-ink-muted">{canBook ? 'Tap seats to pick them' : 'Seat map updates live'}</p>
          )}
        </div>
        {!me ? (
          <LoginToBook event={event} />
        ) : (
          <Button
            disabled={!canBook || picked.length === 0}
            loading={book.isPending}
            onClick={() => book.mutate({ seatIds: picked.map((s) => s.id) })}
          >
            Hold seats
          </Button>
        )}
      </ActionBar>
    </div>
  )
}

function GaPicker({ event, tiers, me, canBook, admissionToken }) {
  const [tierId, setTierId] = useState(() => tiers.find((t) => t.remaining > 0)?.id ?? tiers[0]?.id)
  const [quantity, setQuantity] = useState(1)
  const book = useBooking(event, admissionToken)
  const tier = tiers.find((t) => t.id === tierId)
  const max = Math.min(event.maxTicketsPerUser, tier?.remaining ?? 0)

  return (
    <div>
      <Card>
        <h2 className="mb-4 font-semibold">Choose your tickets</h2>
        <div className="flex flex-col gap-2" role="radiogroup" aria-label="Ticket type">
          {tiers.map((t) => (
            <button
              key={t.id}
              role="radio"
              aria-checked={t.id === tierId}
              disabled={t.remaining === 0}
              onClick={() => {
                setTierId(t.id)
                setQuantity(1)
              }}
              className={`flex items-center gap-3 rounded-xl border p-4 text-left transition disabled:opacity-40 ${
                t.id === tierId ? 'border-accent bg-accent/10' : 'border-line hover:border-ink-muted'
              }`}
            >
              <span className="size-3 shrink-0 rounded" style={{ background: t.colorHex }} />
              <span className="flex-1">
                <span className="block font-semibold">{t.name}</span>
                <span className="text-xs text-ink-muted">{t.remaining > 0 ? `${t.remaining} left` : 'Sold out'}</span>
              </span>
              <span className="font-semibold">{formatMoney(t.pricePaise)}</span>
            </button>
          ))}
        </div>

        <div className="mt-5 flex items-center justify-between">
          <span className="font-semibold">How many?</span>
          <div className="flex items-center gap-3">
            <Button variant="secondary" className="w-11 px-0" aria-label="Fewer"
                    disabled={quantity <= 1} onClick={() => setQuantity((q) => q - 1)}>−</Button>
            <span className="w-6 text-center text-lg font-bold tabular-nums" aria-live="polite">{quantity}</span>
            <Button variant="secondary" className="w-11 px-0" aria-label="More"
                    disabled={quantity >= max} onClick={() => setQuantity((q) => q + 1)}>+</Button>
          </div>
        </div>
      </Card>

      <ActionBar>
        <p className="font-semibold">
          {tier ? `${quantity} × ${tier.name} · ${formatMoney(tier.pricePaise * quantity)}` : 'No tickets available'}
        </p>
        {!me ? (
          <LoginToBook event={event} />
        ) : (
          <Button disabled={!canBook || !tier || max === 0} loading={book.isPending}
                  onClick={() => book.mutate({ tierId, quantity })}>
            Hold tickets
          </Button>
        )}
      </ActionBar>
    </div>
  )
}

/** Sticks to the bottom of the screen on phones so the main button is always in reach. */
function ActionBar({ children }) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-night/95 px-4 py-3 backdrop-blur sm:static sm:mt-4 sm:rounded-card sm:border sm:bg-surface">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">{children}</div>
    </div>
  )
}
