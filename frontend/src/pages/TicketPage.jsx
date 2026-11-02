import { Link, useParams } from 'react-router'
import { useTicket } from '../api/queries.js'
import { Badge, Card, ErrorState, PageLoader } from '../components/ui.jsx'
import { formatDate, formatDateTime, formatMoney, formatTime } from '../lib/format.js'

/** One ticket, ready to show at the gate. Phase 5 adds the 3D flip. */
export default function TicketPage() {
  const { ticketId } = useParams()
  const ticket = useTicket(ticketId)

  if (ticket.isLoading) return <PageLoader />
  if (ticket.isError) return <ErrorState error={ticket.error} onRetry={ticket.refetch} />
  const t = ticket.data

  return (
    <div className="mx-auto max-w-sm">
      <Link to="/tickets" className="mb-4 inline-block text-sm text-ink-muted hover:text-ink">← My tickets</Link>
      <Card className="overflow-hidden p-0">
        <div className="h-3" style={{ background: t.tierColor }} />
        <div className="p-6">
          <p className="text-xs font-semibold tracking-[0.3em] uppercase" style={{ color: t.tierColor }}>FestPass</p>
          <h1 className="font-display mt-1 text-2xl leading-tight font-black">{t.eventTitle}</h1>
          <p className="mt-1 text-ink-muted">{formatDate(t.startsAt)} · {formatTime(t.startsAt)}</p>
          <p className="text-ink-muted">{t.venueName}</p>

          <div className="mt-5 rounded-2xl bg-white p-4">
            <img src={`/api/tickets/${t.id}/qr.png`} alt="Ticket QR code. Show this at the gate."
                 className="mx-auto aspect-square w-full max-w-64" />
          </div>
          {t.checkedInAt && (
            <p className="mt-3 text-center"><Badge tone="success">Checked in {formatDateTime(t.checkedInAt)}</Badge></p>
          )}

          <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-ink-muted">Ticket</dt>
              <dd className="font-semibold" style={{ color: t.tierColor }}>{t.tierName}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Price</dt>
              <dd className="font-semibold">{formatMoney(t.pricePaise)}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-ink-muted">Seat</dt>
              <dd className="font-semibold">{t.seatLabel}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-ink-muted">Holder</dt>
              <dd className="font-semibold">{t.holderName}</dd>
            </div>
          </dl>
          <p className="mt-4 font-mono text-[10px] break-all text-ink-muted">{t.id}</p>
        </div>
      </Card>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <a href={`/api/tickets/${t.id}/pdf`} download
           className="flex min-h-11 items-center justify-center rounded-xl border border-line bg-surface-raised font-semibold hover:border-ink-muted">
          Download PDF
        </a>
        <a href={`/api/tickets/${t.id}/calendar.ics`} download
           className="flex min-h-11 items-center justify-center rounded-xl border border-line bg-surface-raised font-semibold hover:border-ink-muted">
          Add to calendar
        </a>
      </div>
    </div>
  )
}
