// Mirrors backend com.festpass.event.SeatLayoutGenerator so the organizer can preview a
// section before creating it. Keep the numbers in sync with the Java class.

export const SEAT_PITCH = 28
export const ROW_PITCH = 32
export const AISLE_WIDTH = 28
export const SECTION_GAP = 64

export function rowLabel(index) {
  let n = index + 1
  let label = ''
  while (n > 0) {
    n -= 1
    label = String.fromCharCode(65 + (n % 26)) + label
    n = Math.floor(n / 26)
  }
  return label
}

/** Seats of a new section placed below the existing ones, centred on x = 0. */
export function previewSection({ name, tierId, rows, seatsPerRow, aisleAfter = [] }, existingSeats = []) {
  const aisles = new Set(aisleAfter)
  const maxY = existingSeats.length ? Math.max(...existingSeats.map((s) => s.y)) : null
  const firstRowY = maxY == null ? 0 : maxY + SECTION_GAP
  const width = (seatsPerRow - 1) * SEAT_PITCH + aisles.size * AISLE_WIDTH
  const left = -Math.trunc(width / 2)

  const seats = []
  for (let r = 0; r < rows; r++) {
    let aislesPassed = 0
    for (let n = 1; n <= seatsPerRow; n++) {
      if (aisles.has(n - 1)) aislesPassed++
      seats.push({
        id: `preview-${r}-${n}`,
        section: name,
        row: rowLabel(r),
        number: n,
        x: left + (n - 1) * SEAT_PITCH + aislesPassed * AISLE_WIDTH,
        y: firstRowY + r * ROW_PITCH,
        status: 'AVAILABLE',
        tierId,
        preview: true,
      })
    }
  }
  return seats
}

export function boundsOf(seats) {
  if (!seats.length) return null
  return {
    minX: Math.min(...seats.map((s) => s.x)),
    minY: Math.min(...seats.map((s) => s.y)),
    maxX: Math.max(...seats.map((s) => s.x)),
    maxY: Math.max(...seats.map((s) => s.y)),
  }
}
