import { Link, useParams } from 'react-router'
import { useTicket } from '../api/queries.js'
import TicketFlipCard from '../components/TicketFlipCard.jsx'
import { ErrorState, PageLoader } from '../components/ui.jsx'

/** One ticket, opening on its QR code so it is ready to show at the gate. Tap to flip to the details. */
export default function TicketPage() {
  const { ticketId } = useParams()
  const ticket = useTicket(ticketId)

  if (ticket.isLoading) return <PageLoader />
  if (ticket.isError) return <ErrorState error={ticket.error} onRetry={ticket.refetch} />
  const t = ticket.data

  return (
    <div className="mx-auto max-w-sm">
      <Link to="/tickets" className="mb-4 inline-block text-sm text-ink-muted hover:text-ink">← My tickets</Link>
      <TicketFlipCard ticket={t} startOn="back" />

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
