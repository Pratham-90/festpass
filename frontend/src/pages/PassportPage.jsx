import { motion } from 'framer-motion'
import { usePassport } from '../api/queries.js'
import { ButtonLink, Card, EmptyState, ErrorState, PageLoader, PageTitle } from '../components/ui.jsx'
import { formatDate, titleCase } from '../lib/format.js'

const MILESTONE_ICONS = { FIRST_EVENT: '🎈', FIVE_EVENTS: '🖐️', FRONT_ROW_REGULAR: '🪑' }

/** A round rubber stamp for one attended event. Each lands with a little thump, slightly crooked. */
function Stamp({ stamp, index }) {
  const tilt = ((index * 37) % 17) - 8 // -8 to 8 degrees, the same every time for this stamp
  return (
    <motion.li
      initial={{ scale: 2.2, opacity: 0, rotate: tilt - 20 }}
      animate={{ scale: 1, opacity: 1, rotate: tilt }}
      transition={{ delay: 0.15 + Math.min(index, 12) * 0.12, type: 'spring', stiffness: 320, damping: 16 }}
      className="flex aspect-square flex-col items-center justify-center rounded-full border-4 border-double p-3 text-center"
      style={{ borderColor: stamp.color, color: stamp.color }}
    >
      <span className="text-[10px] font-semibold tracking-[0.2em] uppercase">{titleCase(stamp.category)}</span>
      <span className="font-display mt-1 line-clamp-3 text-sm leading-tight font-black text-ink">{stamp.eventTitle}</span>
      <span className="mt-1 text-[11px] text-ink-muted">{formatDate(stamp.checkedInAt)}</span>
      {stamp.frontRow && <span className="mt-0.5 text-[10px] font-semibold text-ink-muted">★ Front row</span>}
    </motion.li>
  )
}

function Milestone({ milestone: m, index }) {
  return (
    <motion.li
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.08 }}
      className={`rounded-card border p-4 ${m.earned ? 'border-success/50 bg-success/10' : 'border-line bg-surface'}`}
    >
      <div className="flex items-start gap-3">
        <span className={`text-3xl ${m.earned ? '' : 'opacity-40 grayscale'}`} aria-hidden>{MILESTONE_ICONS[m.code] ?? '🏅'}</span>
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{m.title} {m.earned && <span className="text-success">✓</span>}</p>
          <p className="text-sm text-ink-muted">{m.description}</p>
          <div className="mt-2 flex items-center gap-2">
            <div className="h-2 flex-1 overflow-hidden rounded-full bg-success/15"
                 role="progressbar" aria-label={m.title} aria-valuemin={0} aria-valuemax={m.target} aria-valuenow={m.progress}>
              <motion.div className="h-full rounded-full bg-success" initial={{ width: 0 }}
                          animate={{ width: `${(m.progress / m.target) * 100}%` }} transition={{ delay: 0.3, duration: 0.6 }} />
            </div>
            <span className="text-xs text-ink-muted tabular-nums">{m.progress} / {m.target}</span>
          </div>
        </div>
      </div>
    </motion.li>
  )
}

export default function PassportPage() {
  const passport = usePassport()

  if (passport.isLoading) return <PageLoader />
  if (passport.isError) return <ErrorState error={passport.error} onRetry={passport.refetch} />
  const { stamps, milestones, eventsAttended } = passport.data

  return (
    <div className="mx-auto max-w-3xl">
      <PageTitle eyebrow="Fest Passport" title={eventsAttended === 1 ? '1 event attended' : `${eventsAttended} events attended`} />

      <Card className="mb-8 bg-gradient-to-br from-accent-2/15 to-transparent">
        {stamps.length === 0 ? (
          <EmptyState
            title="No stamps yet"
            description="Every event you're checked in to at the gate adds a stamp here."
            action={<ButtonLink to="/">Find your first event</ButtonLink>}
          />
        ) : (
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
            {stamps.map((stamp, i) => <Stamp key={stamp.eventId} stamp={stamp} index={i} />)}
          </ul>
        )}
      </Card>

      <h2 className="mb-3 font-display text-xl font-bold">Milestones</h2>
      <ul className="grid gap-3 sm:grid-cols-3">
        {milestones.map((m, i) => <Milestone key={m.code} milestone={m} index={i} />)}
      </ul>
    </div>
  )
}
