import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useEvents } from '../api/queries.js'
import EventCard from '../components/EventCard.jsx'
import { EmptyState, ErrorState, PageLoader, inputClass } from '../components/ui.jsx'
import { CATEGORIES, titleCase } from '../lib/format.js'

/** Waits until typing pauses before searching. */
function useDebounced(value, ms = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}

export default function DiscoverPage() {
  // Filters live in the URL, so a filtered list can be shared or bookmarked.
  const [params, setParams] = useSearchParams()
  const category = params.get('category') ?? ''
  const [query, setQuery] = useState(params.get('q') ?? '')
  const q = useDebounced(query.trim())

  useEffect(() => {
    setParams((p) => {
      const next = new URLSearchParams(p)
      q ? next.set('q', q) : next.delete('q')
      return next
    }, { replace: true })
  }, [q, setParams])

  const setCategory = (value) =>
    setParams((p) => {
      const next = new URLSearchParams(p)
      value ? next.set('category', value) : next.delete('category')
      return next
    })

  const events = useEvents({ q, category, upcoming: true })

  return (
    <div>
      <section className="mb-8">
        <p className="text-sm font-semibold tracking-[0.3em] text-accent uppercase">This week on campus</p>
        <h1 className="font-display mt-2 text-4xl font-black tracking-tight sm:text-6xl">
          Grab your seat <span className="text-accent">before it's gone.</span>
        </h1>
        <p className="mt-3 max-w-xl text-lg text-ink-muted">
          Fests, gigs and society nights at TIET. Live seat maps, flash drops and tickets in your pocket.
        </p>
      </section>

      <div className="mb-6 flex flex-col gap-3">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search events, venues…"
          aria-label="Search events"
          className={`${inputClass} max-w-md`}
        />
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1" role="group" aria-label="Category">
          {['', ...CATEGORIES].map((c) => (
            <button
              key={c || 'all'}
              onClick={() => setCategory(c)}
              aria-pressed={category === c}
              className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-semibold transition ${
                category === c ? 'border-accent bg-accent text-white' : 'border-line text-ink-muted hover:text-ink'
              }`}
            >
              {c ? titleCase(c) : 'All'}
            </button>
          ))}
        </div>
      </div>

      {events.isLoading ? (
        <PageLoader />
      ) : events.isError ? (
        <ErrorState error={events.error} onRetry={events.refetch} />
      ) : events.data.length === 0 ? (
        <EmptyState
          title="No events match"
          description={q || category ? 'Try another search or category.' : 'Nothing is on sale right now. Check back soon!'}
        />
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {events.data.map((event, i) => (
            <EventCard key={event.id} event={event} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}
