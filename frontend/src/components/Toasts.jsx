import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { messageFor } from '../api/errors.js'

const ToastContext = createContext(null)

const TONES = {
  info: 'border-line',
  success: 'border-success/60',
  error: 'border-danger/60',
}

let nextId = 1

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const dismiss = useCallback((id) => setToasts((all) => all.filter((t) => t.id !== id)), [])

  const push = useCallback(
    ({ title, description, tone = 'info', duration = 4500 }) => {
      const id = nextId++
      setToasts((all) => [...all.slice(-3), { id, title, description, tone }])
      setTimeout(() => dismiss(id), duration)
    },
    [dismiss],
  )

  const value = useMemo(
    () => ({
      push,
      success: (title, description) => push({ title, description, tone: 'success' }),
      error: (error) => push({ ...messageFor(error), tone: 'error', duration: 6000 }),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4 sm:items-end sm:right-4 sm:left-auto"
      >
        <AnimatePresence>
          {toasts.map((t) => (
            <motion.div
              key={t.id}
              layout
              initial={{ opacity: 0, y: 24, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12 }}
              className={`pointer-events-auto w-full max-w-sm rounded-card border bg-surface-raised p-4 shadow-xl ${TONES[t.tone]}`}
              role={t.tone === 'error' ? 'alert' : 'status'}
            >
              <div className="flex items-start gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{t.title}</p>
                  {t.description && <p className="mt-0.5 text-sm text-ink-muted">{t.description}</p>}
                </div>
                <button
                  onClick={() => dismiss(t.id)}
                  className="-m-1 rounded p-1 text-ink-muted hover:text-ink"
                  aria-label="Dismiss"
                >
                  ✕
                </button>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

/** toast.success('Saved'), toast.error(apiError), toast.push({ title, description, tone }) */
export function useToast() {
  return useContext(ToastContext)
}
