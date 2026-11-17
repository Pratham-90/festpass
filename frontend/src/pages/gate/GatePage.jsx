import { Link } from 'react-router'
import { useGateEvents } from '../../api/queries.js'
import { EmptyState, ErrorState, PageLoader, PageTitle } from '../../components/ui.jsx'
import { formatDateTime } from '../../lib/format.js'

/** The events this gate staff member can scan for. Pick one to open the scanner. */
export default function GatePage() {
  const events = useGateEvents()

  return (
    <div className="mx-auto max-w-2xl">
      <PageTitle eyebrow="Gate" title="Pick an event to scan" />
      {events.isLoading ? (
        <PageLoader />
      ) : events.isError ? (
        <ErrorState error={events.error} onRetry={events.refetch} />
      ) : events.data.length === 0 ? (
        <EmptyState
          title="No events assigned"
          description="Ask the event's organizer to add your email under Gate staff."
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {events.data.map((e) => (
            <li key={e.id}>
              <Link
                to={`/gate/${e.id}`}
                className="flex items-center gap-4 rounded-card border border-line bg-surface p-4 transition hover:border-ink-muted"
              >
                <span className="text-3xl" aria-hidden>📷</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-display text-lg font-bold">{e.title}</span>
                  <span className="text-sm text-ink-muted">{formatDateTime(e.startsAt)} · {e.venueName}</span>
                </span>
                <span className="text-right">
                  <span className="block font-display text-2xl font-black tabular-nums">{e.checkedIn}</span>
                  <span className="text-xs text-ink-muted">of {e.total} in</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
