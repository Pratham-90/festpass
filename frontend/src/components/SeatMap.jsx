import { useEffect, useMemo, useRef, useState } from 'react'

// Hand-written SVG seat map (no seat-map library, PLAN.md section 2).
// Seats are coloured by tier. Seats other people hold pulse softly, booked seats fade out,
// your selection glows. Wheel or pinch to zoom, drag to pan.

const SEAT = 22 // drawn size; seats are 28 units apart (SeatLayoutGenerator.SEAT_PITCH)
const PAD = 48

function baseView(bounds) {
  if (!bounds) return { x: -100, y: -60, w: 200, h: 120 }
  return {
    x: bounds.minX - PAD,
    y: bounds.minY - PAD - 16, // room for the first section's label
    w: bounds.maxX - bounds.minX + PAD * 2,
    h: bounds.maxY - bounds.minY + PAD * 2 + 16,
  }
}

/** Row labels drawn to the left of each row of each section. */
function rowLabels(seats) {
  const rows = new Map()
  for (const s of seats) {
    const key = `${s.section}|${s.row}`
    const row = rows.get(key)
    if (!row) rows.set(key, { row: s.row, x: s.x, y: s.y })
    else if (s.x < row.x) row.x = s.x
  }
  return [...rows.values()]
}

/**
 * @param seats      [{ id, section, row, number, x, y, status, tierId, color, preview? }]
 * @param selected   Set of seat ids the user has picked
 * @param mine       Set of seat ids in the user's own hold
 * @param onToggle   (seat) => void, for AVAILABLE seats; leave out for a read-only map
 */
export default function SeatMap({ seats, sections = [], bounds, selected = new Set(), mine = new Set(), onToggle, shaking = new Set() }) {
  // Keyed on the numbers, not the object, so a caller passing fresh-but-equal bounds doesn't reset the view.
  const boundsKey = bounds ? `${bounds.minX},${bounds.minY},${bounds.maxX},${bounds.maxY}` : ''
  const base = useMemo(() => baseView(bounds), [boundsKey])
  const [view, setView] = useState(base)
  const svgRef = useRef(null)
  const pointers = useRef(new Map())
  const gesture = useRef({ moved: false, startDistance: 0, startView: null })

  // Reset the view when the layout itself changes (e.g. a section is added in the editor).
  useEffect(() => setView(base), [base])

  const clampWidth = (w) => Math.min(base.w * 1.5, Math.max(base.w / 5, w))

  function zoomAt(factor, clientX, clientY) {
    const rect = svgRef.current.getBoundingClientRect()
    setView((v) => {
      const w = clampWidth(v.w * factor)
      const h = (w / v.w) * v.h
      // Keep the point under the cursor fixed.
      const fx = clientX == null ? 0.5 : (clientX - rect.left) / rect.width
      const fy = clientY == null ? 0.5 : (clientY - rect.top) / rect.height
      return { x: v.x + (v.w - w) * fx, y: v.y + (v.h - h) * fy, w, h }
    })
  }

  // Wheel zoom needs a non-passive listener so the page doesn't scroll at the same time.
  useEffect(() => {
    const svg = svgRef.current
    const onWheel = (e) => {
      e.preventDefault()
      zoomAt(e.deltaY > 0 ? 1.15 : 1 / 1.15, e.clientX, e.clientY)
    }
    svg.addEventListener('wheel', onWheel, { passive: false })
    return () => svg.removeEventListener('wheel', onWheel)
  })

  const distance = () => {
    const [a, b] = [...pointers.current.values()]
    return Math.hypot(a.x - b.x, a.y - b.y)
  }

  const onPointerDown = (e) => {
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
    gesture.current = { moved: false, startView: view, startDistance: pointers.current.size === 2 ? distance() : 0 }
  }

  const onPointerMove = (e) => {
    const previous = pointers.current.get(e.pointerId)
    if (!previous) return
    const rect = svgRef.current.getBoundingClientRect()
    if (pointers.current.size === 1) {
      const dx = e.clientX - previous.x
      const dy = e.clientY - previous.y
      if (Math.abs(dx) + Math.abs(dy) > 2) gesture.current.moved = true
      if (gesture.current.moved) {
        // A drag pans; capture the pointer only now, so a simple tap still reaches the seat.
        if (!svgRef.current.hasPointerCapture(e.pointerId)) svgRef.current.setPointerCapture(e.pointerId)
        setView((v) => ({ ...v, x: v.x - (dx * v.w) / rect.width, y: v.y - (dy * v.h) / rect.height }))
      }
    }
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
    if (pointers.current.size === 2 && gesture.current.startDistance > 0) {
      gesture.current.moved = true
      const start = gesture.current.startView
      const w = clampWidth(start.w * (gesture.current.startDistance / distance()))
      const h = (w / start.w) * start.h
      setView({ x: start.x + (start.w - w) / 2, y: start.y + (start.h - h) / 2, w, h })
    }
  }

  const onPointerUp = (e) => {
    pointers.current.delete(e.pointerId)
  }

  const click = (seat) => {
    if (gesture.current.moved || !onToggle) return
    if (seat.status === 'AVAILABLE' || selected.has(seat.id)) onToggle(seat)
  }

  return (
    <div className="relative">
      <svg
        ref={svgRef}
        viewBox={`${view.x} ${view.y} ${view.w} ${view.h}`}
        className="h-[55dvh] max-h-[560px] min-h-72 w-full touch-none rounded-card border border-line bg-night select-none"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        role="group"
        aria-label="Seat map"
      >
        <defs>
          <filter id="seat-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <text x={0} y={base.y + 10} textAnchor="middle" className="fill-ink-muted text-[10px] font-semibold tracking-[0.4em]">
          STAGE
        </text>

        {sections.map((s) => (
          <text key={s.name} x={(s.minX + s.maxX) / 2} y={s.minY - 22} textAnchor="middle" className="fill-ink-muted text-[12px] font-semibold">
            {s.name}
          </text>
        ))}

        {rowLabels(seats).map((r) => (
          <text key={`${r.row}-${r.y}`} x={r.x - 22} y={r.y + 4} textAnchor="middle" className="fill-ink-muted/60 text-[9px]">
            {r.row}
          </text>
        ))}

        {seats.map((seat) => {
          const isSelected = selected.has(seat.id)
          const isMine = mine.has(seat.id)
          const clickable = onToggle && (seat.status === 'AVAILABLE' || isSelected)
          const label = `${seat.section} row ${seat.row} seat ${seat.number}, ${
            isSelected ? 'selected' : isMine ? 'held for you' : seat.status.toLowerCase()
          }`
          let opacity = 1
          if (seat.status === 'BOOKED' && !isMine) opacity = 0.18
          else if (seat.status === 'HELD' && !isMine && !isSelected) opacity = 0.4
          return (
            <rect
              key={seat.id}
              x={seat.x - SEAT / 2}
              y={seat.y - SEAT / 2}
              width={SEAT}
              height={SEAT}
              rx={6}
              fill={seat.status === 'BOOKED' && !isMine ? 'var(--color-ink-muted)' : seat.color}
              fillOpacity={opacity}
              stroke={isSelected ? 'white' : isMine ? 'var(--color-ink)' : seat.preview ? 'white' : 'none'}
              strokeWidth={isSelected || isMine ? 3 : seat.preview ? 1.5 : 0}
              strokeDasharray={seat.preview ? '3 3' : undefined}
              filter={isSelected ? 'url(#seat-glow)' : undefined}
              className={`transition-[fill-opacity] duration-300 ${clickable ? 'cursor-pointer' : ''} ${
                seat.status === 'HELD' && !isMine && !isSelected ? 'animate-pulse' : ''
              } ${shaking.has(seat.id) ? 'seat-shake' : ''}`}
              role={clickable ? 'button' : undefined}
              tabIndex={clickable ? 0 : undefined}
              aria-label={label}
              aria-pressed={clickable ? isSelected : undefined}
              onClick={() => click(seat)}
              onKeyDown={(e) => {
                if (clickable && (e.key === 'Enter' || e.key === ' ')) {
                  e.preventDefault()
                  onToggle(seat)
                }
              }}
            >
              <title>{label}</title>
            </rect>
          )
        })}
      </svg>

      <div className="absolute top-3 right-3 flex flex-col gap-1">
        {[
          ['+', 'Zoom in', () => zoomAt(1 / 1.3)],
          ['−', 'Zoom out', () => zoomAt(1.3)],
          ['⟲', 'Reset view', () => setView(base)],
        ].map(([symbol, label, action]) => (
          <button
            key={label}
            onClick={action}
            aria-label={label}
            className="size-10 rounded-lg border border-line bg-surface/90 text-lg font-bold hover:bg-surface-raised"
          >
            {symbol}
          </button>
        ))}
      </div>
    </div>
  )
}

/** Colour key below the map. */
export function SeatLegend({ tiers }) {
  return (
    <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-ink-muted">
      {tiers.map((t) => (
        <span key={t.id} className="flex items-center gap-1.5">
          <span className="size-3 rounded" style={{ background: t.colorHex }} /> {t.name}
        </span>
      ))}
      <span className="flex items-center gap-1.5">
        <span className="size-3 animate-pulse rounded bg-ink-muted/50" /> Held
      </span>
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded bg-ink-muted/20" /> Sold
      </span>
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded border-2 border-white" /> Your pick
      </span>
    </div>
  )
}
