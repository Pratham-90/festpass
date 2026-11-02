import { Link } from 'react-router'
import { useOrganizerEvents } from '../../api/queries.js'
import { Badge, ButtonLink, EmptyState, ErrorState, PageLoader, PageTitle } from '../../components/ui.jsx'
import { formatDateTime, titleCase } from '../../lib/format.js'

export const STATUS_TONE = { DRAFT: 'warning', PUBLISHED: 'success', CANCELLED: 'danger' }

export default function OrganizerEventsPage() {
  const events = useOrganizerEvents()

  return (
    <div>
      <PageTitle eyebrow="Organizer" title="Your events">
        <ButtonLink to="/organizer/events/new">+ New event</ButtonLink>
      </PageTitle>

      {events.isLoading ? (
        <PageLoader />
      ) : events.isError ? (
        <ErrorState error={events.error} onRetry={events.refetch} />
      ) : events.data.length === 0 ? (
        <EmptyState
          title="No events yet"
          description="Create a draft, add ticket tiers and a seat layout, then publish it."
          action={<ButtonLink to="/organizer/events/new">Create your first event</ButtonLink>}
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {events.data.map((e) => (
            <li key={e.id}>
              <Link
                to={`/organizer/events/${e.id}`}
                className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-card border border-line bg-surface p-4 transition hover:border-ink-muted"
              >
                <span className="size-3 rounded-full" style={{ background: e.accentColor ?? 'var(--color-line)' }} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold">{e.title}</span>
                  <span className="text-sm text-ink-muted">
                    {formatDateTime(e.startsAt)} · {titleCase(e.seatingMode)}
                    {e.flashDrop && ' · ⚡ Flash drop'}
                  </span>
                </span>
                <Badge tone={STATUS_TONE[e.status]}>{titleCase(e.status)}</Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
