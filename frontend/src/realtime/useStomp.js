import { useEffect, useRef, useSyncExternalStore } from 'react'
import { Client } from '@stomp/stompjs'

// One STOMP connection for the whole app, shared by every component (PLAN.md section 7).
// Components subscribe with useStomp(destination, onMessage); several components can listen
// to the same destination over a single server subscription.
//
// The WebSocket handshake carries the session cookie, so the connection belongs to whoever
// is logged in when it opens. Call reconnectStomp() after login or logout.

const destinations = new Map() // destination -> { handlers: Set, subscription }
const listeners = new Set()
let state = { connected: false, generation: 0 } // generation goes up on every (re)connect
let client = null

function setState(next) {
  state = { ...state, ...next }
  listeners.forEach((l) => l())
}

function brokerUrl() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

function attach(destination, entry) {
  entry.subscription = client.subscribe(destination, (message) => {
    let body
    try {
      body = JSON.parse(message.body)
    } catch {
      return
    }
    entry.handlers.forEach((handler) => handler(body))
  })
}

function ensureClient() {
  if (client) return client
  client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      destinations.forEach((entry, destination) => attach(destination, entry))
      setState({ connected: true, generation: state.generation + 1 })
    },
    onWebSocketClose: () => {
      destinations.forEach((entry) => (entry.subscription = null))
      setState({ connected: false })
    },
    onStompError: (frame) => {
      console.warn('STOMP error:', frame.headers.message)
    },
  })
  client.activate()
  return client
}

function subscribe(destination, handler) {
  ensureClient()
  let entry = destinations.get(destination)
  if (!entry) {
    entry = { handlers: new Set(), subscription: null }
    destinations.set(destination, entry)
    if (client.connected) attach(destination, entry)
  }
  entry.handlers.add(handler)
  return () => {
    entry.handlers.delete(handler)
    if (entry.handlers.size === 0) {
      destinations.delete(destination)
      if (client?.connected) entry.subscription?.unsubscribe()
    }
  }
}

/** Reconnect so the connection picks up a new login (or logout). */
export async function reconnectStomp() {
  if (!client) return
  await client.deactivate()
  client.activate()
}

/** { connected, generation }. generation changes on every reconnect: refetch anything you might have missed. */
export function useStompStatus() {
  ensureClient()
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => state,
  )
}

/**
 * Calls onMessage(parsedJson) for every message on the destination while mounted.
 * Pass enabled=false (or a null destination) to skip, e.g. /user/... queues before login.
 */
export function useStomp(destination, onMessage, { enabled = true } = {}) {
  const handlerRef = useRef(onMessage)
  handlerRef.current = onMessage
  useEffect(() => {
    if (!enabled || !destination) return undefined
    return subscribe(destination, (body) => handlerRef.current(body))
  }, [destination, enabled])
}
