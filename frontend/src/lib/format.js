// Formatting helpers. Money from the API is always in paise (₹1 = 100 paise).

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })
const wholeRupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

export function formatMoney(paise) {
  if (paise == null) return ''
  if (paise === 0) return 'Free'
  return paise % 100 === 0 ? wholeRupees.format(paise / 100) : rupees.format(paise / 100)
}

const dateTime = new Intl.DateTimeFormat('en-IN', {
  weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
})
const dateOnly = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
const timeOnly = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' })

export const formatDateTime = (iso) => (iso ? dateTime.format(new Date(iso)) : '')
export const formatDate = (iso) => (iso ? dateOnly.format(new Date(iso)) : '')
export const formatTime = (iso) => (iso ? timeOnly.format(new Date(iso)) : '')

/** Splits a duration into days/hours/minutes/seconds for countdowns. */
export function splitDuration(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  return {
    days: Math.floor(total / 86400),
    hours: Math.floor((total % 86400) / 3600),
    minutes: Math.floor((total % 3600) / 60),
    seconds: total % 60,
  }
}

/** ISO string -> value for <input type="datetime-local"> in the browser's time zone. */
export function toLocalInput(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** <input type="datetime-local"> value -> ISO string (UTC), or null when empty. */
export function fromLocalInput(value) {
  return value ? new Date(value).toISOString() : null
}

export const CATEGORIES = ['MUSIC', 'DANCE', 'TECH', 'COMEDY', 'DRAMA', 'SPORTS', 'WORKSHOP', 'OTHER']

export const titleCase = (word) => (word ? word.charAt(0) + word.slice(1).toLowerCase().replace(/_/g, ' ') : '')
