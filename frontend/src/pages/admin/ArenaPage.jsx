import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { api } from '../../api/client.js'
import { useOrganizerEvents } from '../../api/queries.js'
import { useStomp } from '../../realtime/useStomp.js'
import SeatMap from '../../components/SeatMap.jsx'
import { Badge, Button, Card, ErrorState, Field, PageLoader, PageTitle, inputClass } from '../../components/ui.jsx'
import { useToast } from '../../components/Toasts.jsx'
import { titleCase } from '../../lib/format.js'

const STRATEGIES = {
  RUSH_SAME_SEATS: 'Every bot goes for the same best seats',
  RANDOM_SEATS: 'Bots pick random seats',
  GA_RUSH: 'Every bot rushes the first ticket tier',
}

const CHECKS = {
  seatsWithSeveralTickets: 'Seats with more than one ticket',
  tiersReservedOverCapacity: 'Tiers reserved past capacity',
  tiersWithMoreTicketsThanCapacity: 'Tiers with more tickets than capacity',
  paidOrdersWithoutTickets: 'Paid orders without tickets',
  bookedSeatsWithoutTicket: 'Booked seats without a ticket',
}

const n = (value) => Number(value ?? 0).toLocaleString('en-IN')

/**
 * The Stress Arena (PLAN.md section 8, feature 8): hundreds of bots fight over a sandbox copy of
 * an event through the real API, live on screen, and an integrity report proves nothing was oversold.
 */
export default function ArenaPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const runs = useQuery({ queryKey: ['arena', 'runs'], queryFn: () => api('/admin/arena/runs') })
  const [runId, setRunId] = useState(null)
  const current = runId ?? runs.data?.[0]?.id ?? null

  const reset = useMutation({
    mutationFn: () => api('/admin/arena/reset', { method: 'POST' }),
    onSuccess: (r) => {
      setRunId(null)
      queryClient.invalidateQueries({ queryKey: ['arena'] })
      toast.success('Arena reset', `${r.sandboxEventsDeleted} sandbox events and ${n(r.botsDeleted)} bots deleted.`)
    },
    onError: toast.error,
  })

  return (
    <div className="flex flex-col gap-6">
      <PageTitle eyebrow="Admin" title="Stress Arena">
        <Button
          variant="danger"
          loading={reset.isPending}
          onClick={() => window.confirm('Delete every sandbox event, bot account and run?') && reset.mutate()}
        >
          Reset arena
        </Button>
      </PageTitle>
      <p className="-mt-4 max-w-2xl text-ink-muted">
        Bots log in, then all hit the real booking API at the same instant on a hidden copy of the event.
        Real events are never touched. Watch the seats fill, then check the integrity report.
      </p>

      <StartForm onStarted={(run) => { setRunId(run.id); queryClient.invalidateQueries({ queryKey: ['arena', 'runs'] }) }} />

      {runs.isLoading ? <PageLoader /> : runs.isError ? <ErrorState error={runs.error} onRetry={runs.refetch} /> : current ? (
        <RunPanel key={current} runId={current} />
      ) : (
        <Card className="text-center text-ink-muted">No runs yet. Pick an event above and start one.</Card>
      )}

      {runs.data?.length > 1 && (
        <Card>
          <h2 className="mb-3 font-display text-lg font-bold">Recent runs</h2>
          <ul className="flex flex-col divide-y divide-line text-sm">
            {runs.data.map((r) => (
              <li key={r.id}>
                <button onClick={() => setRunId(r.id)} className={`flex w-full flex-wrap items-center gap-3 py-2 text-left ${r.id === current ? 'text-ink' : 'text-ink-muted hover:text-ink'}`}>
                  <span className="font-semibold">#{r.id}</span>
                  <span>{n(r.bots)} bots · {titleCase(r.strategy)}</span>
                  <span className="ml-auto">
                    {r.result?.allZero === true ? <Badge tone="success">0 oversold</Badge>
                      : r.result?.allZero === false ? <Badge tone="danger">Problems</Badge>
                      : <Badge>{titleCase(r.status)}</Badge>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}

function StartForm({ onStarted }) {
  const toast = useToast()
  const events = useOrganizerEvents()
  const published = useMemo(() => (events.data ?? []).filter((e) => e.status === 'PUBLISHED'), [events.data])
  const [eventId, setEventId] = useState('')
  const [bots, setBots] = useState(500)
  const [strategy, setStrategy] = useState('RUSH_SAME_SEATS')
  const event = published.find((e) => e.id === Number(eventId)) ?? published[0]
  const allowed = event?.seatingMode === 'GENERAL' ? ['GA_RUSH'] : ['RUSH_SAME_SEATS', 'RANDOM_SEATS']

  useEffect(() => {
    if (!allowed.includes(strategy)) setStrategy(allowed[0])
  }, [allowed, strategy])

  const start = useMutation({
    mutationFn: () => api('/admin/arena/runs', { method: 'POST', body: { sourceEventId: event.id, bots: Number(bots), strategy } }),
    onSuccess: onStarted,
    onError: toast.error,
  })

  if (events.isLoading) return <Card><PageLoader /></Card>

  return (
    <Card>
      <form onSubmit={(e) => { e.preventDefault(); start.mutate() }} className="grid gap-4 sm:grid-cols-[2fr_1fr_2fr_auto] sm:items-end">
        <Field label="Event to copy">
          <select className={inputClass} value={event?.id ?? ''} onChange={(e) => setEventId(e.target.value)}>
            {published.map((e) => (
              <option key={e.id} value={e.id}>{e.title} ({titleCase(e.seatingMode)})</option>
            ))}
          </select>
        </Field>
        <Field label="Bots" error={start.error?.fieldErrors?.bots}>
          <input type="number" min={1} max={2000} className={inputClass} value={bots} onChange={(e) => setBots(e.target.value)} />
        </Field>
        <Field label="Strategy" hint={STRATEGIES[strategy]} error={start.error?.fieldErrors?.strategy}>
          <select className={inputClass} value={strategy} onChange={(e) => setStrategy(e.target.value)}>
            {allowed.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
          </select>
        </Field>
        <Button type="submit" loading={start.isPending} disabled={!event} className="sm:mb-6">⚔️ Start the fight</Button>
      </form>
    </Card>
  )
}

function RunPanel({ runId }) {
  const queryClient = useQueryClient()
  const run = useQuery({
    queryKey: ['arena', 'run', runId],
    queryFn: () => api(`/admin/arena/runs/${runId}`),
    refetchInterval: (q) => (['PREPARING', 'RUNNING'].includes(q.state.data?.status) ? 2000 : false),
  })
  const [progress, setProgress] = useState(null)

  useStomp(`/topic/arena/${runId}`, (message) => {
    setProgress(message)
    if (message.report) {
      queryClient.invalidateQueries({ queryKey: ['arena'] })
    }
  })

  if (run.isLoading) return <PageLoader />
  if (run.isError) return <ErrorState error={run.error} onRetry={run.refetch} />

  const r = run.data
  const report = progress?.report ?? r.result
  const status = progress?.status ?? r.status
  const live = progress ?? {}
  const sandboxEventId = live.sandboxEventId ?? r.sandboxEventId
  const done = status === 'FINISHED' || status === 'FAILED'
  const finished = live.botsFinished ?? (done ? r.bots : 0)

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="font-display text-xl font-bold">Run #{r.id}</h2>
          <Badge tone={status === 'FAILED' ? 'danger' : done ? 'success' : 'warning'}>{titleCase(status)}</Badge>
          <span className="text-sm text-ink-muted">{n(r.bots)} bots · {titleCase(r.strategy)}</span>
          {live.phase && <span className="ml-auto text-sm font-semibold">{live.phase}</span>}
        </div>

        <div className="mt-4">
          <div className="mb-1 flex justify-between text-sm">
            <span className="text-ink-muted">{done ? 'All bots finished' : status === 'RUNNING' ? 'Bots finished' : `Bots logged in`}</span>
            <span className="tabular-nums">{status === 'RUNNING' || done ? `${n(finished)} / ${n(r.bots)}` : `${n(live.botsLoggedIn)} / ${n(r.bots)}`}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-accent/15" role="progressbar" aria-label="Bots finished"
               aria-valuemin={0} aria-valuemax={r.bots} aria-valuenow={finished}>
            <div className="h-full rounded-full bg-accent transition-[width] duration-200"
                 style={{ width: `${((status === 'RUNNING' || done ? finished : live.botsLoggedIn ?? 0) / Math.max(1, r.bots)) * 100}%` }} />
          </div>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Stat label="Hold attempts" value={n(live.holdAttempts ?? report?.holdAttempts)} />
          <Stat label="Holds won" value={n(live.holdsWon ?? report?.holdsWon)} />
          <Stat label="Lost to another bot" value={n(live.conflicts ?? report?.conflicts)} />
          <Stat label="Paid" value={n(live.ordersPaid ?? report?.ordersPaid)} />
          <Stat label="Hold latency p50" value={`${n(live.p50Ms ?? report?.p50Ms)} ms`} />
          <Stat label="Hold latency p95" value={`${n(live.p95Ms ?? report?.p95Ms)} ms`} />
          <Stat label="Failed / abandoned" value={`${n(live.paymentsFailed ?? report?.paymentsFailed)} / ${n(live.abandoned ?? report?.abandoned)}`} />
          <Stat label="Fight time" value={`${((live.elapsedMs ?? report?.raceMs ?? 0) / 1000).toFixed(1)} s`} />
        </div>
      </Card>

      {sandboxEventId && <SandboxView runId={runId} sandboxEventId={sandboxEventId} strategy={r.strategy} />}
      {report && <Report report={report} />}
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div>
      <p className="text-xs text-ink-muted">{label}</p>
      <p className="font-display text-2xl font-bold tabular-nums">{value}</p>
    </div>
  )
}

/** The sandbox filling up live: seats for seated strategies, a meter per tier for GA. */
function SandboxView({ runId, sandboxEventId, strategy }) {
  const queryClient = useQueryClient()
  const key = ['arena', 'seatmap', runId]
  const map = useQuery({ queryKey: key, queryFn: () => api(`/admin/arena/runs/${runId}/seatmap`), retry: 3 })
  const [stats, setStats] = useState(null)

  useStomp(`/topic/events/${sandboxEventId}/seats`, (deltas) => {
    const byId = new Map(deltas.map((d) => [d.seatId, d.status]))
    queryClient.setQueryData(key, (old) => old && { ...old, seats: old.seats.map((s) => (byId.has(s.id) ? { ...s, status: byId.get(s.id) } : s)) })
  })
  useStomp(`/topic/events/${sandboxEventId}/stats`, setStats)

  if (map.isLoading) return <Card><PageLoader /></Card>
  if (map.isError) return null

  if (strategy === 'GA_RUSH') {
    return (
      <Card>
        <h2 className="mb-4 font-display text-lg font-bold">Tickets allocated</h2>
        <ul className="flex flex-col gap-4">
          {map.data.tiers.map((t) => {
            const remaining = stats?.remainingByTier?.[t.id] ?? t.remaining
            const taken = t.capacity - remaining
            return (
              <li key={t.id}>
                <div className="mb-1.5 flex justify-between gap-3 text-sm">
                  <span className="flex items-center gap-2 font-semibold"><span className="size-2.5 rounded-full" style={{ background: t.colorHex }} /> {t.name}</span>
                  <span className="tabular-nums">{n(taken)} / {n(t.capacity)}</span>
                </div>
                <div className="h-3 overflow-hidden rounded-full" style={{ background: `color-mix(in srgb, ${t.colorHex} 18%, transparent)` }}>
                  <div className="h-full rounded-full transition-[width] duration-200" style={{ width: `${(taken / Math.max(1, t.capacity)) * 100}%`, background: t.colorHex }} />
                </div>
              </li>
            )
          })}
        </ul>
      </Card>
    )
  }

  // Empty seats recede; held seats pulse amber; booked seats light up in the accent colour.
  const seats = map.data.seats.map((s) => ({
    ...s,
    color: s.status === 'BOOKED' ? 'var(--color-accent)' : s.status === 'HELD' ? 'var(--color-warning)' : 'var(--color-line)',
    status: s.status === 'HELD' ? 'HELD' : 'AVAILABLE',
  }))
  return (
    <Card>
      <h2 className="mb-1 font-display text-lg font-bold">The sandbox, live</h2>
      <p className="mb-4 text-sm text-ink-muted">A hidden copy of the event. Seats change colour as bots grab them.</p>
      <SeatMap seats={seats} sections={map.data.sections} bounds={map.data.bounds} />
      <div className="mt-3 flex flex-wrap gap-4 text-xs text-ink-muted">
        <span className="flex items-center gap-1.5"><span className="size-3 rounded" style={{ background: 'var(--color-line)' }} /> Free</span>
        <span className="flex items-center gap-1.5"><span className="size-3 animate-pulse rounded bg-warning" /> Held in a checkout</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-accent" /> Sold</span>
      </div>
    </Card>
  )
}

function Report({ report }) {
  if (report.error) {
    return <Card className="border-danger/50"><p className="font-semibold text-danger">The run failed: {report.error}</p></Card>
  }
  const problems = Object.keys(CHECKS).reduce((sum, k) => sum + Number(report.integrity?.[k] ?? 0), 0)
  return (
    <Card className={report.allZero ? 'border-success/50' : 'border-danger/50'}>
      <div className="flex flex-col items-center gap-2 py-4 text-center">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 260, damping: 14 }}
          className={`rounded-full px-8 py-4 font-display text-4xl font-black text-white sm:text-6xl ${report.allZero ? 'bg-flash-ok' : 'bg-flash-bad'}`}
        >
          {report.allZero ? '✓ 0 oversold' : `✕ ${problems} problems`}
        </motion.div>
        <p className="mt-2 text-ink-muted">
          {n(report.bots)} bots · {n(report.holdAttempts)} hold attempts in {(report.raceMs / 1000).toFixed(1)} s ·{' '}
          {n(report.ticketsSold)} sold + {n(report.ticketsHeld)} held in abandoned checkouts, of {n(report.capacity)}
        </p>
      </div>
      <h3 className="mt-2 mb-2 font-semibold">Integrity checks (all must be 0)</h3>
      <ul className="grid gap-2 sm:grid-cols-2">
        {Object.entries(CHECKS).map(([key, label]) => {
          const value = Number(report.integrity?.[key] ?? 0)
          return (
            <li key={key} className="flex items-center justify-between gap-3 rounded-xl border border-line px-3 py-2 text-sm">
              <span>{label}</span>
              <span className={`font-bold tabular-nums ${value === 0 ? 'text-success' : 'text-danger'}`}>{value === 0 ? '✓ 0' : `✕ ${value}`}</span>
            </li>
          )
        })}
      </ul>
      {report.errors > 0 && (
        <p className="mt-3 text-sm text-warning">{n(report.errors)} bot requests failed: {JSON.stringify(report.errorsByCode)}</p>
      )}
    </Card>
  )
}
