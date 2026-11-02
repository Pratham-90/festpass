import { useEffect, useRef } from 'react'
import { useServerNow } from '../lib/serverClock.js'

/**
 * Countdown ring for a seat hold: accent colour, amber in the last minute,
 * red in the last 15 seconds. Calls onExpire once at zero.
 */
export default function HoldTimer({ expiresAt, totalSeconds, onExpire }) {
  const now = useServerNow(250)
  const left = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000))
  const expiredRef = useRef(false)

  useEffect(() => {
    if (left === 0 && !expiredRef.current) {
      expiredRef.current = true
      onExpire?.()
    }
  }, [left, onExpire])

  const radius = 52
  const circumference = 2 * Math.PI * radius
  const fraction = Math.min(1, left / totalSeconds)
  const stage = left <= 15 ? 'text-danger' : left <= 60 ? 'text-warning' : 'text-accent'
  const label = `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`

  return (
    <div className={`relative size-36 ${stage}`} role="timer" aria-label={`${label} left to pay`}>
      <svg viewBox="0 0 120 120" className="size-full -rotate-90">
        <circle cx="60" cy="60" r={radius} fill="none" stroke="currentColor" strokeOpacity="0.15" strokeWidth="10" />
        <circle
          cx="60"
          cy="60"
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fraction)}
          className="transition-[stroke-dashoffset] duration-300 ease-linear"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-mono text-3xl font-black tabular-nums">{label}</span>
        <span className="text-xs text-ink-muted">held for you</span>
      </div>
    </div>
  )
}
