import { useEffect, useState } from 'react'

// Countdowns (sale opening, hold timers) must follow the server's clock, not the phone's,
// which can be minutes off. We measure the difference once at startup.
let offsetMs = 0

export async function syncServerClock() {
  try {
    const sentAt = Date.now()
    const res = await fetch('/api/health', { credentials: 'same-origin' })
    const receivedAt = Date.now()
    const { time } = await res.json()
    offsetMs = Date.parse(time) - (sentAt + receivedAt) / 2
  } catch {
    offsetMs = 0 // offline or the API is down: fall back to the device clock
  }
}

export const serverNow = () => Date.now() + offsetMs

/** Re-renders every `intervalMs` and returns the current server time in ms. */
export function useServerNow(intervalMs = 1000) {
  const [now, setNow] = useState(serverNow)
  useEffect(() => {
    const id = setInterval(() => setNow(serverNow()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}
