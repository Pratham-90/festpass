import { Link } from 'react-router'
import { messageFor } from '../api/errors.js'

// Small shared building blocks. All colours, radii and fonts come from the @theme tokens
// in styles/index.css, so the visual design can be changed in one place.

const BUTTON_VARIANTS = {
  primary: 'bg-accent text-white hover:brightness-110 shadow-lg shadow-accent/20',
  secondary: 'bg-surface-raised text-ink border border-line hover:border-ink-muted',
  ghost: 'text-ink-muted hover:text-ink hover:bg-surface-raised',
  danger: 'bg-danger/15 text-danger border border-danger/40 hover:bg-danger/25',
}

export function Button({ variant = 'primary', loading = false, className = '', children, disabled, ...props }) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2 font-semibold transition
        disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_VARIANTS[variant]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

export function ButtonLink({ variant = 'primary', className = '', ...props }) {
  return (
    <Link
      {...props}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2 font-semibold transition
        ${BUTTON_VARIANTS[variant]} ${className}`}
    />
  )
}

export function Card({ className = '', children, ...props }) {
  return (
    <div {...props} className={`rounded-card border border-line bg-surface p-5 ${className}`}>
      {children}
    </div>
  )
}

export function Badge({ tone = 'muted', children }) {
  const tones = {
    muted: 'bg-surface-raised text-ink-muted',
    accent: 'bg-accent/15 text-accent',
    success: 'bg-success/15 text-success',
    warning: 'bg-warning/15 text-warning',
    danger: 'bg-danger/15 text-danger',
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {children}
    </span>
  )
}

export function Spinner({ small = false }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent
        ${small ? 'size-4' : 'size-8 text-accent'}`}
    />
  )
}

export function PageLoader() {
  return (
    <div className="flex min-h-[50dvh] items-center justify-center">
      <Spinner />
    </div>
  )
}

export function EmptyState({ title, description, action }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 px-4 py-16 text-center">
      <div className="text-4xl" aria-hidden>
        🎟️
      </div>
      <h2 className="font-display text-xl font-bold">{title}</h2>
      {description && <p className="text-ink-muted">{description}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry }) {
  const { title, description } = messageFor(error)
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 px-4 py-16 text-center">
      <div className="text-4xl" aria-hidden>
        😵
      </div>
      <h2 className="font-display text-xl font-bold">{title}</h2>
      {description && <p className="text-ink-muted">{description}</p>}
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** A labelled form field. Pass `error` (string) to show a field error. */
export function Field({ label, error, hint, children }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-semibold text-ink-muted">{label}</span>
      {children}
      {hint && !error && <span className="text-xs text-ink-muted">{hint}</span>}
      {error && <span className="text-sm text-danger">{error}</span>}
    </label>
  )
}

export const inputClass =
  'min-h-11 w-full rounded-xl border border-line bg-night px-3 py-2 text-ink placeholder:text-ink-muted/60 ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60'

export function PageTitle({ eyebrow, title, children }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        {eyebrow && <p className="text-sm font-semibold tracking-[0.2em] text-accent uppercase">{eyebrow}</p>}
        <h1 className="font-display text-3xl font-black tracking-tight sm:text-4xl">{title}</h1>
      </div>
      {children}
    </div>
  )
}
