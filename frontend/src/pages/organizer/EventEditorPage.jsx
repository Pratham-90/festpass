import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client.js'
import { keys, useGateStaff, useOrganizerEvent, useOrganizerSeatMap } from '../../api/queries.js'
import { useStomp } from '../../realtime/useStomp.js'
import SeatMap, { SeatLegend } from '../../components/SeatMap.jsx'
import { Badge, Button, Card, ErrorState, Field, PageLoader, inputClass } from '../../components/ui.jsx'
import { useToast } from '../../components/Toasts.jsx'
import { formatMoney, titleCase } from '../../lib/format.js'
import { boundsOf, previewSection } from '../../lib/seatLayout.js'
import EventForm from './EventForm.jsx'
import { STATUS_TONE } from './OrganizerEventsPage.jsx'

const PALETTE = ['#ff3d81', '#7c5cff', '#22d3ee', '#f59e0b', '#22e39a', '#f43f5e']

/** Refreshes everything this page shows about the event. */
function useRefreshEvent(eventId) {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: keys.orgEvent(eventId) })
    queryClient.invalidateQueries({ queryKey: keys.orgSeatmap(eventId) })
    queryClient.invalidateQueries({ queryKey: keys.orgEvents })
  }
}

export default function EventEditorPage() {
  // A number, like event.id, so query keys match when the page refreshes its data.
  const eventId = Number(useParams().eventId)
  const event = useOrganizerEvent(eventId)
  if (event.isLoading) return <PageLoader />
  if (event.isError) return <ErrorState error={event.error} onRetry={event.refetch} />
  return <Editor event={event.data} />
}

function Editor({ event }) {
  const toast = useToast()
  const refresh = useRefreshEvent(event.id)
  const draft = event.status === 'DRAFT'
  const [stats, setStats] = useState(null)

  // Once published, the organizer watches the sale live.
  useStomp(`/topic/events/${event.id}/stats`, setStats, { enabled: !draft })

  const update = useMutation({
    mutationFn: (body) => api(`/organizer/events/${event.id}`, { method: 'PUT', body }),
    onSuccess: () => {
      refresh()
      toast.success('Details saved')
    },
    onError: (error) => error.code !== 'VALIDATION_FAILED' && toast.error(error),
  })

  const publish = useMutation({
    mutationFn: () => api(`/organizer/events/${event.id}/publish`, { method: 'POST' }),
    onSuccess: () => {
      refresh()
      toast.success('Published! 🎉', 'Your event is now on the Discover page.')
    },
    onError: toast.error,
  })

  const tiers = event.tiers.map((t) => ({ ...t, remaining: stats?.remainingByTier?.[t.id] ?? t.remaining }))

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <Link to="/organizer" className="text-sm text-ink-muted hover:text-ink">← Your events</Link>
          <h1 className="font-display mt-1 text-3xl font-black tracking-tight">{event.title}</h1>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge tone={STATUS_TONE[event.status]}>{titleCase(event.status)}</Badge>
            <Badge>{titleCase(event.seatingMode)}</Badge>
            {event.flashDrop && <Badge tone="accent">⚡ Flash drop</Badge>}
            {stats && <Badge tone="success">{stats.viewers} viewing · {stats.soldLastMinute} sold in the last minute</Badge>}
          </div>
        </div>
        {draft ? (
          <Button onClick={() => publish.mutate()} loading={publish.isPending}>Publish</Button>
        ) : (
          <Link to={`/events/${event.slug}`} className="font-semibold text-accent">View public page →</Link>
        )}
      </div>

      <TiersCard event={event} tiers={tiers} draft={draft} />
      {event.seatingMode === 'SEATED' && <SeatLayoutCard event={event} tiers={tiers} draft={draft} />}

      <Card>
        <h2 className="mb-4 font-display text-xl font-bold">Details</h2>
        <EventForm
          key={event.id}
          event={event}
          onSubmit={update.mutate}
          submitting={update.isPending}
          error={update.error}
          submitLabel="Save details"
          seatingLocked={event.tiers.length > 0}
        />
      </Card>

      <GateStaffCard event={event} />
    </div>
  )
}

function TiersCard({ event, tiers, draft }) {
  const toast = useToast()
  const refresh = useRefreshEvent(event.id)
  const general = event.seatingMode === 'GENERAL'
  const [form, setForm] = useState({ name: '', price: '', capacity: '', colorHex: PALETTE[tiers.length % PALETTE.length] })

  const add = useMutation({
    mutationFn: () =>
      api(`/organizer/events/${event.id}/tiers`, {
        method: 'POST',
        body: {
          name: form.name,
          pricePaise: Math.round(Number(form.price) * 100),
          capacity: general ? Number(form.capacity) : undefined,
          colorHex: form.colorHex,
        },
      }),
    onSuccess: () => {
      refresh()
      setForm({ name: '', price: '', capacity: '', colorHex: PALETTE[(tiers.length + 1) % PALETTE.length] })
    },
    onError: (error) => error.code !== 'VALIDATION_FAILED' && toast.error(error),
  })
  const errors = add.error?.fieldErrors ?? {}

  return (
    <Card>
      <h2 className="mb-4 font-display text-xl font-bold">Ticket tiers</h2>
      {tiers.length === 0 ? (
        <p className="text-ink-muted">No tiers yet. Add at least one before publishing.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-line">
          {tiers.map((t) => {
            const taken = t.capacity - t.remaining
            return (
              <li key={t.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3">
                <span className="size-4 rounded" style={{ background: t.colorHex }} />
                <span className="min-w-0 flex-1 font-semibold">{t.name}</span>
                <span className="text-sm text-ink-muted">{formatMoney(t.pricePaise)}</span>
                <span className="w-40 text-right text-sm">
                  {draft ? `${t.capacity} ${general ? 'tickets' : 'seats'}` : `${taken} of ${t.capacity} taken`}
                </span>
                {!draft && (
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-raised">
                    <div className="h-full rounded-full transition-all" style={{ width: `${(taken / Math.max(1, t.capacity)) * 100}%`, background: t.colorHex }} />
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {draft && (
        <form onSubmit={(e) => { e.preventDefault(); add.mutate() }} className="mt-5 grid gap-3 border-t border-line pt-5 sm:grid-cols-[1fr_120px_120px_60px_auto] sm:items-end">
          <Field label="Name" error={errors.name}>
            <input required maxLength={60} className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. VIP" />
          </Field>
          <Field label="Price (₹)" error={errors.pricePaise}>
            <input required type="number" min={0} step="0.01" className={inputClass} value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
          </Field>
          {general ? (
            <Field label="Capacity" error={errors.capacity}>
              <input required type="number" min={1} className={inputClass} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
            </Field>
          ) : (
            <p className="pb-3 text-xs text-ink-muted">Seats come from sections below.</p>
          )}
          <Field label="Colour">
            <input type="color" className="h-11 w-full cursor-pointer rounded-xl border border-line bg-night" value={form.colorHex} onChange={(e) => setForm({ ...form, colorHex: e.target.value })} />
          </Field>
          <Button type="submit" loading={add.isPending}>Add tier</Button>
        </form>
      )}
    </Card>
  )
}

function SeatLayoutCard({ event, tiers, draft }) {
  const toast = useToast()
  const refresh = useRefreshEvent(event.id)
  const seatmap = useOrganizerSeatMap(event.id)
  const [form, setForm] = useState({ name: '', tierId: '', rows: 4, seatsPerRow: 12, aisles: '' })

  const aisleAfter = form.aisles.split(',').map((s) => Number(s.trim())).filter((n) => Number.isInteger(n) && n > 0)
  const tierId = Number(form.tierId) || tiers[0]?.id
  const tier = tiers.find((t) => t.id === tierId)
  const existing = seatmap.data?.seats ?? []
  const rows = Number(form.rows)
  const seatsPerRow = Number(form.seatsPerRow)
  const validSize = rows >= 1 && rows <= 52 && seatsPerRow >= 1 && seatsPerRow <= 60

  // Live preview: the existing layout plus the section being designed, drawn dashed.
  const preview = useMemo(() => {
    if (!draft || !tier || !validSize) return []
    return previewSection({ name: form.name || 'New section', tierId, rows, seatsPerRow, aisleAfter }, existing)
      .map((s) => ({ ...s, color: tier.colorHex }))
  }, [draft, tier, validSize, form.name, tierId, rows, seatsPerRow, form.aisles, existing])

  const add = useMutation({
    mutationFn: () =>
      api(`/organizer/events/${event.id}/sections`, {
        method: 'POST',
        body: { name: form.name, tierId, rows, seatsPerRow, aisleAfter },
      }),
    onSuccess: (section) => {
      refresh()
      toast.success(`Added ${section.name}`, `${section.seatCount} seats`)
      setForm({ ...form, name: '' })
    },
    onError: (error) => error.code !== 'VALIDATION_FAILED' && toast.error(error),
  })
  const errors = add.error?.fieldErrors ?? {}

  if (seatmap.isLoading) return <Card><PageLoader /></Card>
  if (seatmap.isError) return <Card><ErrorState error={seatmap.error} onRetry={seatmap.refetch} /></Card>

  const allSeats = [...existing, ...preview]
  const sections = [
    ...seatmap.data.sections,
    ...(preview.length ? [{ name: `${form.name || 'New section'} (preview)`, ...boundsOf(preview) }] : []),
  ]

  return (
    <Card>
      <h2 className="mb-1 font-display text-xl font-bold">Seat layout</h2>
      <p className="mb-4 text-sm text-ink-muted">
        {existing.length} seats in {seatmap.data.sections.length} section{seatmap.data.sections.length === 1 ? '' : 's'}.
        {draft && ' Sections are added below the existing ones; the dashed seats are a preview.'}
      </p>

      {allSeats.length ? (
        <>
          <SeatMap seats={allSeats} sections={sections} bounds={boundsOf(allSeats)} />
          <SeatLegend tiers={tiers} />
        </>
      ) : (
        <p className="rounded-card border border-dashed border-line p-8 text-center text-ink-muted">
          {tiers.length ? 'Fill in the form to preview your first section.' : 'Add a ticket tier first, then design sections for it.'}
        </p>
      )}

      {draft && tiers.length > 0 && (
        <form onSubmit={(e) => { e.preventDefault(); add.mutate() }} className="mt-5 grid gap-3 border-t border-line pt-5 sm:grid-cols-3">
          <Field label="Section name" error={errors.name}>
            <input required maxLength={40} className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Front" />
          </Field>
          <Field label="Tier" error={errors.tierId}>
            <select className={inputClass} value={tierId} onChange={(e) => setForm({ ...form, tierId: e.target.value })}>
              {tiers.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </Field>
          <Field label="Aisles after seat" error={errors.aisleAfter} hint="Comma-separated, e.g. 4, 12">
            <input className={inputClass} value={form.aisles} onChange={(e) => setForm({ ...form, aisles: e.target.value })} placeholder="4, 12" />
          </Field>
          <Field label="Rows" error={errors.rows} hint="1–52, lettered A, B, …">
            <input type="number" min={1} max={52} required className={inputClass} value={form.rows} onChange={(e) => setForm({ ...form, rows: e.target.value })} />
          </Field>
          <Field label="Seats per row" error={errors.seatsPerRow} hint="1–60">
            <input type="number" min={1} max={60} required className={inputClass} value={form.seatsPerRow} onChange={(e) => setForm({ ...form, seatsPerRow: e.target.value })} />
          </Field>
          <div className="flex items-end">
            <Button type="submit" className="w-full" loading={add.isPending} disabled={!validSize}>
              Add {validSize ? rows * seatsPerRow : ''} seats
            </Button>
          </div>
        </form>
      )}
    </Card>
  )
}

function GateStaffCard({ event }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const staff = useGateStaff(event.id)
  const [email, setEmail] = useState('')
  const add = useMutation({
    mutationFn: () => api(`/organizer/events/${event.id}/gate-staff`, { method: 'POST', body: { email } }),
    onSuccess: (person) => {
      queryClient.invalidateQueries({ queryKey: keys.gateStaff(event.id) })
      toast.success(`${person.name} can now scan tickets`)
      setEmail('')
    },
    onError: (error) => error.code !== 'VALIDATION_FAILED' && toast.error(error),
  })

  return (
    <Card>
      <h2 className="mb-1 font-display text-xl font-bold">Gate staff</h2>
      <p className="mb-4 text-sm text-ink-muted">People who can scan tickets at the entrance. They need a gate staff account (an admin can set that up).</p>
      {staff.isLoading ? (
        <PageLoader />
      ) : staff.isError ? (
        <ErrorState error={staff.error} onRetry={staff.refetch} />
      ) : staff.data.length === 0 ? (
        <p className="text-sm text-ink-muted">Nobody assigned yet.</p>
      ) : (
        <ul className="flex flex-col gap-1 text-sm">
          {staff.data.map((p) => <li key={p.userId}><span className="font-semibold">{p.name}</span> <span className="text-ink-muted">· {p.email}</span></li>)}
        </ul>
      )}
      <form onSubmit={(e) => { e.preventDefault(); add.mutate() }} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="flex-1">
          <Field label="Email" error={add.error?.fieldErrors?.email}>
            <input type="email" required className={inputClass} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="gate@festpass.local" />
          </Field>
        </div>
        <Button type="submit" className="sm:mt-7" loading={add.isPending}>Assign</Button>
      </form>
    </Card>
  )
}
