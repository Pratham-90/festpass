import { useEffect, useRef } from 'react'
import { splitDuration } from '../lib/format.js'
import { useServerNow } from '../lib/serverClock.js'

/** Ticks down to `to` (ISO) on the server's clock. Calls onDone once when it reaches zero. */
export default function Countdown({ to, onDone, size = 'md' }) {
  const now = useServerNow()
  const remaining = Date.parse(to) - now
  const done = remaining <= 0
  const doneRef = useRef(false)

  useEffect(() => {
    if (done && !doneRef.current) {
      doneRef.current = true
      onDone?.()
    }
  }, [done, onDone])

  const { days, hours, minutes, seconds } = splitDuration(remaining)
  const parts = [
    ...(days > 0 ? [[days, 'd']] : []),
    [hours, 'h'],
    [minutes, 'm'],
    [seconds, 's'],
  ]
  const big = size === 'lg'

  return (
    <span className={`inline-flex items-baseline gap-1.5 font-mono tabular-nums ${big ? 'text-3xl font-black sm:text-5xl' : 'font-semibold'}`}>
      {parts.map(([value, unit]) => (
        <span key={unit}>
          {String(value).padStart(2, '0')}
          <span className={`${big ? 'text-base' : 'text-xs'} text-ink-muted`}>{unit}</span>
        </span>
      ))}
    </span>
  )
}
