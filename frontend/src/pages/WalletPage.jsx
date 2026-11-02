import { Link } from 'react-router'
import { motion } from 'framer-motion'
import { useMyTickets } from '../api/queries.js'
import { Badge, ButtonLink, EmptyState, ErrorState, PageLoader, PageTitle } from '../components/ui.jsx'
import { formatDateTime } from '../lib/format.js'
import { useServerNow } from '../lib/serverClock.js'

export function TicketCard({ ticket, index = 0 }) {
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(index, 8) * 0.05 }}>
      <Link
        to={`/tickets/${ticket.id}`}
        className="flex items-stretch overflow-hidden rounded-card border border-line bg-surface transition hover:border-ink-muted"
      >
        <div className="w-2 shrink-0" style={{ background: ticket.tierColor }} />
        <div className="flex min-w-0 flex-1 items-center gap-4 p-4">
          <div className="min-w-0 flex-1">
            <p className="truncate font-display text-lg font-bold">{ticket.eventTitle}</p>
            <p className="text-sm text-ink-muted">{formatDateTime(ticket.startsAt)} · {ticket.venueName}</p>
            <p className="mt-1 text-sm">
              <span className="font-semibold" style={{ color: ticket.tierColor }}>{ticket.tierName}</span>
              <span className="text-ink-muted"> · {ticket.seatLabel}</span>
            </p>
          </div>
          {ticket.checkedInAt ? <Badge tone="success">Checked in</Badge> : <span className="text-2xl" aria-hidden>›</span>}
        </div>
      </Link>
    </motion.div>
  )
}

export default function WalletPage() {
  const tickets = useMyTickets()
  const now = useServerNow(60_000)

  if (tickets.isLoading) return <PageLoader />
  if (tickets.isError) return <ErrorState error={tickets.error} onRetry={tickets.refetch} />

  const upcoming = tickets.data.filter((t) => Date.parse(t.endsAt) > now)
  const past = tickets.data.filter((t) => Date.parse(t.endsAt) <= now)

  return (
    <div className="mx-auto max-w-2xl">
      <PageTitle eyebrow="Wallet" title="My tickets" />
      {tickets.data.length === 0 ? (
        <EmptyState
          title="No tickets yet"
          description="When you book, your tickets show up here with their QR codes."
          action={<ButtonLink to="/">Find an event</ButtonLink>}
        />
      ) : (
        <>
          <div className="flex flex-col gap-3">
            {upcoming.map((t, i) => <TicketCard key={t.id} ticket={t} index={i} />)}
            {upcoming.length === 0 && <p className="text-ink-muted">Nothing coming up. Time to book something!</p>}
          </div>
          {past.length > 0 && (
            <>
              <h2 className="mt-10 mb-3 font-semibold text-ink-muted">Past events</h2>
              <div className="flex flex-col gap-3 opacity-70">
                {past.map((t) => <TicketCard key={t.id} ticket={t} />)}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
