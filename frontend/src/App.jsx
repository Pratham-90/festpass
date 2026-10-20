import { useEffect, useState } from 'react'
import { api } from './api/client.js'

// Phase 0 placeholder: proves the React app can reach the Spring Boot API.
export default function App() {
  const [health, setHealth] = useState({ state: 'loading' })

  useEffect(() => {
    api('/health')
      .then((data) => setHealth({ state: 'ok', data }))
      .catch((error) => setHealth({ state: 'error', error }))
  }, [])

  return (
    <main className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center gap-8 px-4 py-12">
      <div>
        <p className="text-sm font-semibold tracking-[0.3em] text-accent uppercase">UCS553 · TIET</p>
        <h1 className="font-display mt-2 text-5xl font-black tracking-tight sm:text-6xl">
          Fest<span className="text-accent">Pass</span>
        </h1>
        <p className="mt-3 text-lg text-ink-muted">
          Flash-sale tickets for college fests. Live seats, zero overselling.
        </p>
      </div>

      <section className="rounded-card border border-line bg-surface p-5">
        <h2 className="text-sm font-semibold text-ink-muted">Backend status</h2>
        <HealthStatus health={health} />
      </section>
    </main>
  )
}

function HealthStatus({ health }) {
  if (health.state === 'loading') {
    return (
      <p className="mt-2 flex items-center gap-2 text-ink-muted">
        <span className="size-2.5 animate-pulse rounded-full bg-warning" /> Checking /api/health…
      </p>
    )
  }
  if (health.state === 'error') {
    return (
      <div className="mt-2">
        <p className="flex items-center gap-2 font-semibold text-danger">
          <span className="size-2.5 rounded-full bg-danger" /> Can't reach the API
        </p>
        <p className="mt-1 text-sm text-ink-muted">
          {health.error.message}. Is the backend running on port 8080?
        </p>
      </div>
    )
  }
  const { status, database, time } = health.data
  const up = status === 'UP'
  return (
    <div className="mt-2">
      <p className={`flex items-center gap-2 font-semibold ${up ? 'text-success' : 'text-warning'}`}>
        <span className={`size-2.5 rounded-full ${up ? 'bg-success' : 'bg-warning'}`} /> API {status}
      </p>
      <p className="mt-1 text-sm text-ink-muted">
        Database {database} · server time {new Date(time).toLocaleTimeString()}
      </p>
    </div>
  )
}
