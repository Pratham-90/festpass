// Sound and vibration for the gate scanner. Tones are generated with the Web Audio API,
// so there are no audio files to load. Browsers only allow sound after the user has
// tapped something, which the "Start scanning" button takes care of.

let audio = null

function context() {
  if (!audio) {
    const AudioContext = window.AudioContext || window.webkitAudioContext
    if (!AudioContext) return null
    audio = new AudioContext()
  }
  if (audio.state === 'suspended') audio.resume()
  return audio
}

function tone(ctx, { frequency, start, duration, type = 'sine', volume = 0.2 }) {
  const oscillator = ctx.createOscillator()
  const gain = ctx.createGain()
  oscillator.type = type
  oscillator.frequency.value = frequency
  gain.gain.setValueAtTime(volume, ctx.currentTime + start)
  gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + start + duration)
  oscillator.connect(gain).connect(ctx.destination)
  oscillator.start(ctx.currentTime + start)
  oscillator.stop(ctx.currentTime + start + duration)
}

/** Call from a tap handler once, so later sounds are allowed to play. */
export function unlockSound() {
  context()
}

/** A rising two-note chime and a short buzz of the phone. */
export function signalSuccess() {
  const ctx = context()
  if (ctx) {
    tone(ctx, { frequency: 880, start: 0, duration: 0.15 })
    tone(ctx, { frequency: 1320, start: 0.12, duration: 0.25 })
  }
  navigator.vibrate?.(80)
}

/** A low double buzz, in sound and vibration. */
export function signalFailure() {
  const ctx = context()
  if (ctx) {
    tone(ctx, { frequency: 180, start: 0, duration: 0.2, type: 'square', volume: 0.15 })
    tone(ctx, { frequency: 140, start: 0.25, duration: 0.3, type: 'square', volume: 0.15 })
  }
  navigator.vibrate?.([150, 80, 150])
}
