import { useMemo } from 'react'
import { Link, useParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useReducedMotion } from 'framer-motion'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { keys, useDashboard, useOrganizerSeatMap } from '../../api/queries.js'
import { useStomp, useStompStatus } from '../../realtime/useStomp.js'
import SeatMap from '../../components/SeatMap.jsx'
import { Badge, Card, ErrorState, PageLoader } from '../../components/ui.jsx'
import { formatDateTime, formatMoney, formatTime, titleCase } from '../../lib/format.js'
import { STATUS_TONE } from './OrganizerEventsPage.jsx'

const HEAT = ['var(--color-heat-1)', 'var(--color-heat-2)', 'var(--color-heat-3)', 'var(--color-heat-4)', 'var(--color-heat-5)']

const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 })
const count = (n) => (n >= 10_000 ? compact.format(n) : n.toLocaleString('en-IN'))
const ordinal = (n) => `${n}${['th', 'st', 'nd', 'rd'][n % 100 > 10 && n % 100 < 14 ? 0 : Math.min(n % 10, 4) % 4]}`

/**
 * Organizer "mission control" (PLAN.md section 8, feature 6). Everything updates live: each
 * stats or check-in message from the server reloads the numbers, with no page refresh.
 */
export default function DashboardPage() {
  const eventId = Number(useParams().eventId)
  const queryClient = useQueryClient()
  const dashboard = useDashboard(eventId)
  const { connected } = useStompStatus()

  const reload = () => queryClient.invalidateQueries({ queryKey: keys.dashboard(eventId) })
  useStomp(`/topic/events/${eventId}/stats`, reload)
  useStomp(`/topic/events/${eventId}/checkins`, reload)

  if (dashboard.isLoading) return <PageLoader />
  if (dashboard.isError) return <ErrorState error={dashboard.error} onRetry={dashboard.refetch} />
  const d = dashboard.data
  const available = d.capacity - d.ticketsSold - d.ticketsHeld

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <Link to={`/organizer/events/${d.eventId}`} className="text-sm text-ink-muted hover:text-ink">← Edit event</Link>
          <h1 className="font-display mt-1 text-3xl font-black tracking-tight">{d.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Badge tone={STATUS_TONE[d.status]}>{titleCase(d.status)}</Badge>
            <Badge tone={connected ? 'success' : 'warning'}>
              <span className={`size-1.5 rounded-full ${connected ? 'animate-pulse bg-success' : 'bg-warning'}`} />
              {connected ? 'Live' : 'Reconnecting'}
            </Badge>
            <span className="text-sm text-ink-muted">Event starts {formatDateTime(d.startsAt)}</span>
          </div>
        </div>
      </div>

      {/* Headline numbers. Tickets sold is the one hero figure. */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="sm:col-span-2 lg:row-span-2 lg:flex lg:flex-col lg:justify-center">
          <p className="text-sm text-ink-muted">Tickets sold</p>
          <p className="font-display text-6xl font-black sm:text-7xl">{count(d.ticketsSold)}</p>
          <p className="mt-1 text-sm text-ink-muted">
            of {count(d.capacity)} · {count(d.ticketsHeld)} held in checkouts · {count(Math.max(0, available))} still available
          </p>
        </Card>
        <StatTile label="Revenue" value={formatMoney(d.revenuePaise) === 'Free' ? '₹0' : formatMoney(d.revenuePaise)} />
        <StatTile label="Viewing now" value={count(d.viewers)} hint="open event pages" />
        <StatTile label="Sold in the last minute" value={count(d.soldLastMinute)} />
        <StatTile label="Checked in" value={count(d.checkedIn)} hint={`of ${count(d.ticketsSold)} tickets`} />
      </div>

      <Card>
        <h2 className="font-display text-xl font-bold">Sales over time</h2>
        <p className="mb-4 text-sm text-ink-muted">Running total of tickets sold, per {d.bucket.toLowerCase().replace(/s$/, '')}.</p>
        <SalesChart points={d.salesOverTime} bucket={d.bucket} />
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 font-display text-xl font-bold">How full each tier is</h2>
          <ul className="flex flex-col gap-4">
            {d.tiers.map((t) => (
              <Meter
                key={t.tierId}
                color={t.colorHex}
                label={t.name}
                value={t.sold}
                max={t.capacity}
                figure={`${count(t.sold)} / ${count(t.capacity)}`}
                detail={`${formatMoney(t.pricePaise)} each · ${t.held} held · ${formatMoney(t.revenuePaise) === 'Free' ? '₹0' : formatMoney(t.revenuePaise)}`}
              />
            ))}
          </ul>
        </Card>
        <Card>
          <h2 className="mb-4 font-display text-xl font-bold">Check-in at the gate</h2>
          <ul>
            <Meter
              color="var(--color-success)"
              label="People inside"
              value={d.checkedIn}
              max={Math.max(1, d.ticketsSold)}
              figure={`${count(d.checkedIn)} / ${count(d.ticketsSold)}`}
              detail={d.ticketsSold ? `${Math.round((d.checkedIn / d.ticketsSold) * 100)}% of ticket holders have arrived` : 'No tickets sold yet'}
            />
          </ul>
        </Card>
      </div>

      {d.seatingMode === 'SEATED' && <HeatMap eventId={eventId} heat={d.seatHeat} />}
    </div>
  )
}

function StatTile({ label, value, hint }) {
  return (
    <Card>
      <p className="text-sm text-ink-muted">{label}</p>
      <p className="font-display mt-1 text-3xl font-bold">{value}</p>
      {hint && <p className="text-xs text-ink-muted">{hint}</p>}
    </Card>
  )
}

/** One row of a meter list: name, a thin bar against its own limit, and the figure. */
function Meter({ color, label, value, max, figure, detail }) {
  const share = max > 0 ? Math.min(1, value / max) : 0
  return (
    <li>
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <span className="flex items-center gap-2 font-semibold">
          <span className="size-2.5 rounded-full" style={{ background: color }} /> {label}
        </span>
        <span className="text-sm tabular-nums">{figure}</span>
      </div>
      {/* The track is a lighter step of the fill's own colour. */}
      <div className="h-2 overflow-hidden rounded-full" style={{ background: `color-mix(in srgb, ${color} 18%, transparent)` }}
           role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={max} aria-valuenow={value}>
        <div className="h-full rounded-full transition-[width] duration-500" style={{ width: `${share * 100}%`, background: color }} />
      </div>
      {detail && <p className="mt-1 text-xs text-ink-muted">{detail}</p>}
    </li>
  )
}

function SalesChart({ points, bucket }) {
  const reduceMotion = useReducedMotion()
  const tick = (iso) => (bucket === 'DAYS'
    ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
    : bucket === 'HOURS'
      ? new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric' })
      : formatTime(iso))

  if (points.length === 0) {
    return <p className="rounded-card border border-dashed border-line p-10 text-center text-ink-muted">No sales yet. This fills in live as tickets sell.</p>
  }
  const data = points.map((p) => ({ ...p, time: Date.parse(p.at) }))

  return (
    <>
      <div className="h-64 w-full" role="img" aria-label={`Line chart of tickets sold over time, now at ${data[data.length - 1].cumulative}.`}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--color-line)" vertical={false} />
            <XAxis
              dataKey="time" type="number" scale="time" domain={['dataMin', 'dataMax']}
              tickFormatter={tick} tick={{ fill: 'var(--color-ink-muted)', fontSize: 11 }}
              axisLine={{ stroke: 'var(--color-line)' }} tickLine={false} minTickGap={48}
            />
            <YAxis
              allowDecimals={false} width={40} tickFormatter={count}
              tick={{ fill: 'var(--color-ink-muted)', fontSize: 11 }} axisLine={false} tickLine={false}
            />
            <Tooltip content={<SalesTooltip tick={tick} unit={bucket.toLowerCase().replace(/s$/, '')} />} cursor={{ stroke: 'var(--color-ink-muted)', strokeWidth: 1 }} />
            <Area
              type="stepAfter" dataKey="cumulative" name="Tickets sold"
              stroke="var(--color-accent)" strokeWidth={2} fill="var(--color-accent)" fillOpacity={0.1}
              dot={false} activeDot={{ r: 5, stroke: 'var(--color-surface)', strokeWidth: 2 }}
              isAnimationActive={!reduceMotion}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer text-ink-muted hover:text-ink">Show as a table</summary>
        <div className="mt-2 max-h-60 overflow-auto">
          <table className="w-full text-left tabular-nums">
            <thead className="text-ink-muted">
              <tr><th className="py-1 pr-4 font-semibold">Time</th><th className="py-1 pr-4 font-semibold">Sold</th><th className="py-1 font-semibold">Total so far</th></tr>
            </thead>
            <tbody>
              {points.filter((p) => p.tickets > 0).map((p) => (
                <tr key={p.at} className="border-t border-line">
                  <td className="py-1 pr-4">{tick(p.at)}</td><td className="py-1 pr-4">{p.tickets}</td><td className="py-1">{p.cumulative}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  )
}

function SalesTooltip({ active, payload, tick, unit }) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload
  return (
    <div className="rounded-xl border border-line bg-surface-raised px-3 py-2 text-sm shadow-xl">
      <p className="text-ink-muted">{tick(point.at)}</p>
      <p className="font-semibold">{point.cumulative.toLocaleString('en-IN')} sold in total</p>
      <p className="text-ink-muted">{point.tickets} in this {unit}</p>
    </div>
  )
}

/** The seat map coloured by the order seats sold in: the first fifth lightest, the last fifth darkest. */
function HeatMap({ eventId, heat }) {
  const seatmap = useOrganizerSeatMap(eventId)
  const orderBySeat = useMemo(() => new Map(heat.map((h, i) => [h.seatId, { order: h.order, rank: i + 1 }])), [heat])

  if (seatmap.isLoading) return <Card><PageLoader /></Card>
  if (seatmap.isError) return <Card><ErrorState error={seatmap.error} onRetry={seatmap.refetch} /></Card>

  const seats = seatmap.data.seats.map((s) => {
    const sold = orderBySeat.get(s.id)
    return sold
      ? { ...s, status: 'AVAILABLE', color: HEAT[Math.min(4, Math.floor(sold.order * 5))], note: `sold ${ordinal(sold.rank)}` }
      : { ...s, status: 'AVAILABLE', color: 'var(--color-line)', note: 'not sold yet' }
  })

  return (
    <Card>
      <h2 className="font-display text-xl font-bold">What sold first</h2>
      <p className="mb-4 text-sm text-ink-muted">
        {heat.length ? `${heat.length} seats sold. Hover or tap a seat to see where it came in the order.` : 'No seats sold yet. Seats light up here as they sell.'}
      </p>
      <SeatMap seats={seats} sections={seatmap.data.sections} bounds={seatmap.data.bounds} />
      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-ink-muted">
        <span>Sold first</span>
        <span className="flex gap-0.5">
          {HEAT.map((c) => <span key={c} className="h-3 w-6 first:rounded-l last:rounded-r" style={{ background: c }} />)}
        </span>
        <span>Sold last</span>
        <span className="ml-3 flex items-center gap-1.5"><span className="size-3 rounded" style={{ background: 'var(--color-line)' }} /> Not sold yet</span>
      </div>
    </Card>
  )
}
