import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Html5Qrcode } from 'html5-qrcode'
import { api } from '../../api/client.js'
import { messageFor } from '../../api/errors.js'
import { keys, useCheckinStats, useGateEvents } from '../../api/queries.js'
import { useStomp } from '../../realtime/useStomp.js'
import { Button, inputClass } from '../../components/ui.jsx'
import { signalFailure, signalSuccess, unlockSound } from '../../lib/feedback.js'
import { formatTime } from '../../lib/format.js'

const READER_ID = 'gate-qr-reader'
const SAME_CODE_COOLDOWN_MS = 4000

/** What the full-screen flash says for each scan result. */
function describe(scan) {
  switch (scan.result) {
    case 'VALID':
      return { ok: true, icon: '✓', title: 'Welcome in!', lines: [scan.holderName, [scan.tierName, scan.seatLabel].filter(Boolean).join(' · ')] }
    case 'ALREADY_USED':
      return { ok: false, icon: '✕', title: 'Already scanned', lines: [`at ${formatTime(scan.checkedInAt)} by ${scan.checkedInBy}`, scan.holderName] }
    case 'WRONG_EVENT':
      return { ok: false, icon: '✕', title: 'Wrong event', lines: [scan.message] }
    default:
      return { ok: false, icon: '✕', title: 'Not valid', lines: [scan.message] }
  }
}

/**
 * The gate scanner (PLAN.md section 8, feature 5): full-screen camera on a phone. A valid ticket
 * flashes the whole screen green with a chime and a short vibration; anything else flashes red
 * with the reason. The counter at the top is live.
 *
 * Phones only allow the camera on HTTPS (or localhost): see "Gate scanner on a phone" in the README.
 * Without a camera, codes can be typed or pasted instead.
 */
export default function ScannerPage() {
  const eventId = Number(useParams().eventId)
  const queryClient = useQueryClient()
  const stats = useCheckinStats(eventId)
  const events = useGateEvents()
  const event = events.data?.find((e) => e.id === eventId)

  const [started, setStarted] = useState(false)
  const [cameraError, setCameraError] = useState(null)
  const [flash, setFlash] = useState(null)
  const [manualCode, setManualCode] = useState('')
  const lastScan = useRef({ code: null, at: 0 })
  const busy = useRef(false)
  const flashTimer = useRef(null)

  useStomp(`/topic/events/${eventId}/checkins`, (update) => {
    queryClient.setQueryData(keys.checkinStats(eventId), (old) => ({ ...old, ...update }))
  })

  const showFlash = useCallback((content) => {
    clearTimeout(flashTimer.current)
    setFlash(content)
    flashTimer.current = setTimeout(() => setFlash(null), content.ok ? 1800 : 3500)
    if (content.ok) signalSuccess()
    else signalFailure()
  }, [])

  const check = useMutation({
    mutationFn: (code) => api('/checkin', { method: 'POST', body: { eventId, code } }),
    onSuccess: (scan) => {
      showFlash(describe(scan))
      queryClient.invalidateQueries({ queryKey: keys.checkinStats(eventId) })
    },
    onError: (error) => {
      const { title, description } = messageFor(error)
      showFlash({ ok: false, icon: '!', title, lines: [description] })
    },
    onSettled: () => {
      busy.current = false
    },
  })

  const submit = useCallback(
    (code) => {
      const text = code.trim()
      const now = Date.now()
      // The camera sees the same QR many times a second: ignore repeats and scans made mid-request.
      if (!text || busy.current) return
      if (text === lastScan.current.code && now - lastScan.current.at < SAME_CODE_COOLDOWN_MS) return
      lastScan.current = { code: text, at: now }
      busy.current = true
      check.mutate(text)
    },
    [check],
  )
  const submitRef = useRef(submit)
  submitRef.current = submit

  // Start the camera once the user has tapped "Start scanning" (the tap also unlocks sound).
  useEffect(() => {
    if (!started) return undefined
    let cancelled = false
    const scanner = new Html5Qrcode(READER_ID)
    const running = scanner
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: (w, h) => { const side = Math.floor(Math.min(w, h) * 0.75); return { width: side, height: side } } },
        (text) => submitRef.current(text),
        () => {}, // "no QR in this frame" fires constantly; nothing to do
      )
      .then(() => true)
      .catch((error) => {
        if (!cancelled) {
          setCameraError(
            window.isSecureContext
              ? `The camera couldn't start (${error?.message ?? error}). Check the camera permission, or type the code below.`
              : 'Phones only allow the camera over HTTPS. Open the https:// address (see the README), or type the code below.',
          )
        }
        return false
      })
    return () => {
      cancelled = true
      running.then((ok) => ok && scanner.stop().then(() => scanner.clear()).catch(() => {}))
    }
  }, [started])

  useEffect(() => () => clearTimeout(flashTimer.current), [])

  const checkedIn = stats.data?.checkedIn ?? event?.checkedIn ?? 0
  const total = stats.data?.total ?? event?.total ?? 0

  return (
    <div className="flex min-h-dvh flex-col bg-black text-ink">
      <header className="flex items-center gap-3 border-b border-line/60 bg-night px-4 py-3">
        <Link to="/gate" className="flex size-11 items-center justify-center rounded-lg text-xl" aria-label="Back to events">←</Link>
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{event?.title ?? 'Gate scanner'}</p>
          <p className="text-xs text-ink-muted">Scan tickets at the entrance</p>
        </div>
        <div className="text-right" aria-live="polite">
          <p className="font-display text-3xl leading-none font-black tabular-nums">{checkedIn}</p>
          <p className="text-xs text-ink-muted">of {total} in</p>
        </div>
      </header>

      <div className="relative flex flex-1 flex-col items-center justify-center">
        {!started ? (
          <div className="flex flex-col items-center gap-4 p-8 text-center">
            <span className="text-6xl" aria-hidden>📷</span>
            <p className="max-w-xs text-ink-muted">Point the camera at the QR code on each ticket. Your phone will ask to use the camera.</p>
            <Button onClick={() => { unlockSound(); setStarted(true) }}>Start scanning</Button>
          </div>
        ) : cameraError ? (
          <p className="max-w-sm p-8 text-center text-warning" role="alert">{cameraError}</p>
        ) : null}
        {/* html5-qrcode puts the camera video inside this element. */}
        <div id={READER_ID} className={`w-full max-w-xl ${started && !cameraError ? '' : 'hidden'}`} />
      </div>

      <form
        className="flex gap-2 border-t border-line/60 bg-night p-3"
        onSubmit={(e) => {
          e.preventDefault()
          unlockSound()
          submit(manualCode)
          setManualCode('')
        }}
      >
        <input
          className={inputClass}
          value={manualCode}
          onChange={(e) => setManualCode(e.target.value)}
          placeholder="No camera? Type or paste the ticket code"
          aria-label="Ticket code"
          autoComplete="off"
        />
        <Button type="submit" variant="secondary" loading={check.isPending} disabled={!manualCode.trim()}>Check</Button>
      </form>

      <AnimatePresence>
        {flash && (
          <motion.button
            key={`${flash.title}-${lastScan.current.at}`}
            type="button"
            onClick={() => setFlash(null)}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.12 }}
            className={`fixed inset-0 z-50 flex flex-col items-center justify-center gap-3 p-8 text-center text-white ${
              flash.ok ? 'bg-flash-ok' : 'bg-flash-bad'
            }`}
            role="alert"
          >
            <motion.span
              initial={{ scale: 0.4 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 400, damping: 14 }}
              className="text-[9rem] leading-none font-black"
              aria-hidden
            >
              {flash.icon}
            </motion.span>
            <span className="font-display text-4xl font-black">{flash.title}</span>
            {flash.lines.filter(Boolean).map((line) => (
              <span key={line} className="text-xl">{line}</span>
            ))}
            <span className="mt-6 text-sm opacity-80">Tap to scan the next ticket</span>
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  )
}
