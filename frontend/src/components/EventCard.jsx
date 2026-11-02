import { Link } from 'react-router'
import { motion } from 'framer-motion'
import Countdown from './Countdown.jsx'
import { Badge } from './ui.jsx'
import { formatDateTime, formatMoney, titleCase } from '../lib/format.js'
import { useServerNow } from '../lib/serverClock.js'

/** Banner image, or a gradient in the event's accent colour when there is none. */
export function EventBanner({ event, className = '' }) {
  const accent = event.accentColor || 'var(--color-accent)'
  if (event.bannerUrl) {
    return <img src={event.bannerUrl} alt="" className={`object-cover ${className}`} loading="lazy" />
  }
  return (
    <div
      aria-hidden
      className={className}
      style={{
        background: `radial-gradient(circle at 20% 20%, ${accent}cc, transparent 55%),
                     radial-gradient(circle at 85% 80%, var(--color-accent-2), transparent 50%),
                     var(--color-surface-raised)`,
      }}
    />
  )
}

export default function EventCard({ event, index = 0 }) {
  const now = useServerNow(1000)
  const saleOpens = Date.parse(event.saleOpensAt)
  const saleOpen = now >= saleOpens && now < Date.parse(event.saleClosesAt)

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index, 8) * 0.05 }}
      whileHover={{ y: -4 }}
    >
      <Link
        to={`/events/${event.slug}`}
        className="group block overflow-hidden rounded-card border border-line bg-surface transition hover:border-ink-muted"
      >
        <div className="relative">
          <EventBanner event={event} className="h-36 w-full" />
          <div className="absolute top-3 left-3 flex gap-2">
            <Badge>{titleCase(event.category)}</Badge>
            {event.flashDrop && <Badge tone="accent">⚡ Flash drop</Badge>}
          </div>
        </div>
        <div className="flex flex-col gap-2 p-4">
          <h3 className="font-display text-lg leading-tight font-bold group-hover:text-accent">{event.title}</h3>
          <p className="text-sm text-ink-muted">
            {formatDateTime(event.startsAt)} · {event.venueName}
          </p>
          <div className="mt-1 flex items-center justify-between gap-2 text-sm">
            <span className="font-semibold">{event.fromPricePaise != null && `From ${formatMoney(event.fromPricePaise)}`}</span>
            {now < saleOpens ? (
              <span className="flex items-center gap-1 text-warning">
                Opens in <Countdown to={event.saleOpensAt} />
              </span>
            ) : saleOpen ? (
              <span className="text-success">On sale</span>
            ) : (
              <span className="text-ink-muted">Sales closed</span>
            )}
          </div>
        </div>
      </Link>
    </motion.div>
  )
}
