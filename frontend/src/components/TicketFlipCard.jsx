import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Badge } from './ui.jsx'
import { formatDate, formatDateTime, formatMoney, formatTime } from '../lib/format.js'

/**
 * A ticket that flips in 3D between its details (front) and its QR code (back).
 * Tap, or press Enter/Space, to flip. With "reduce motion" on, it switches sides without spinning.
 *
 * @param startOn    'front' or 'back'
 * @param revealAfter  ms after which a card that starts on the front flips itself once (the "ticket reveal")
 */
export default function TicketFlipCard({ ticket: t, startOn = 'front', revealAfter }) {
  const [showQr, setShowQr] = useState(startOn === 'back')

  useEffect(() => {
    if (revealAfter == null) return undefined
    const id = setTimeout(() => setShowQr(true), revealAfter)
    return () => clearTimeout(id)
  }, [revealAfter])

  const flip = () => setShowQr((v) => !v)

  return (
    <div className="[perspective:1200px]">
      <motion.div
        role="button"
        tabIndex={0}
        aria-label={showQr ? 'Ticket QR code. Tap to see the details.' : 'Ticket details. Tap to see the QR code.'}
        onClick={flip}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            flip()
          }
        }}
        animate={{ rotateY: showQr ? 180 : 0 }}
        transition={{ type: 'spring', stiffness: 160, damping: 20 }}
        // Both faces sit in the same grid cell, so the card is as tall as the taller face.
        className="grid cursor-pointer rounded-card [transform-style:preserve-3d] focus-visible:outline-2 focus-visible:outline-accent"
      >
        <Face hidden={showQr}>
          <div className="h-3" style={{ background: t.tierColor }} />
          <div className="flex flex-1 flex-col p-6">
            <p className="text-xs font-semibold tracking-[0.3em] text-ink-muted uppercase">FestPass</p>
            <h2 className="font-display mt-1 text-2xl leading-tight font-black">{t.eventTitle}</h2>
            <p className="mt-1 text-ink-muted">{formatDate(t.startsAt)} · {formatTime(t.startsAt)}</p>
            <p className="text-ink-muted">{t.venueName}</p>

            <dl className="mt-6 grid grid-cols-2 gap-4 text-sm">
              <div>
                <dt className="text-ink-muted">Ticket</dt>
                <dd className="flex items-center gap-2 font-semibold">
                  <span className="size-2.5 rounded-full" style={{ background: t.tierColor }} /> {t.tierName}
                </dd>
              </div>
              <div>
                <dt className="text-ink-muted">Price</dt>
                <dd className="font-semibold">{formatMoney(t.pricePaise)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-ink-muted">Seat</dt>
                <dd className="text-lg font-bold">{t.seatLabel}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-ink-muted">Holder</dt>
                <dd className="font-semibold">{t.holderName}</dd>
              </div>
            </dl>
            <p className="mt-auto pt-6 text-center text-sm text-ink-muted">Tap to show the QR code ↻</p>
          </div>
        </Face>

        <Face hidden={!showQr} back>
          <div className="h-3" style={{ background: t.tierColor }} />
          <div className="flex flex-1 flex-col items-center p-6">
            <p className="self-start truncate text-sm font-semibold">{t.eventTitle}</p>
            <p className="self-start text-sm text-ink-muted">{t.tierName} · {t.seatLabel}</p>
            <div className="mt-4 w-full rounded-2xl bg-white p-4">
              <img
                src={`/api/tickets/${t.id}/qr.png`}
                alt="Ticket QR code. Show this at the gate."
                className="mx-auto aspect-square w-full max-w-64"
              />
            </div>
            {t.checkedInAt ? (
              <p className="mt-3"><Badge tone="success">Checked in {formatDateTime(t.checkedInAt)}</Badge></p>
            ) : (
              <p className="mt-3 text-sm text-ink-muted">Show this at the gate</p>
            )}
            <p className="mt-auto pt-4 font-mono text-[10px] break-all text-ink-muted">{t.id}</p>
          </div>
        </Face>
      </motion.div>
    </div>
  )
}

function Face({ back = false, hidden, children }) {
  return (
    <div
      aria-hidden={hidden}
      className="col-start-1 row-start-1 flex min-h-[30rem] flex-col overflow-hidden rounded-card border border-line bg-surface [backface-visibility:hidden]"
      style={back ? { transform: 'rotateY(180deg)' } : undefined}
    >
      {children}
    </div>
  )
}
