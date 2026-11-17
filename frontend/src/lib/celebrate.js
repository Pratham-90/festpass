import confetti from 'canvas-confetti'

// Confetti in the given colours (the ticket tier's colour, usually).
// disableForReducedMotion makes canvas-confetti do nothing for people who ask for less motion.
export function celebrate(colors = ['#ff3d81', '#7c5cff', '#22e39a']) {
  const palette = [...new Set([...colors, '#ffffff'])]
  const shared = { colors: palette, disableForReducedMotion: true, zIndex: 100 }
  confetti({ ...shared, particleCount: 120, spread: 80, startVelocity: 45, origin: { y: 0.4 } })
  setTimeout(() => {
    confetti({ ...shared, particleCount: 60, angle: 60, spread: 60, origin: { x: 0, y: 0.6 } })
    confetti({ ...shared, particleCount: 60, angle: 120, spread: 60, origin: { x: 1, y: 0.6 } })
  }, 250)
}
