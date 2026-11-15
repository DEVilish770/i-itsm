import { Client } from '@stomp/stompjs'

// One shared STOMP client over native WebSocket. Callers subscribe to topics and get an unsubscribe fn.
// STOMP subscriptions die with their connection, so every active subscription is kept here and made again
// on each (re)connect. Connection listeners hear the real connect / disconnect events.
let client = null
const subscriptions = new Set() // { topic, handler, sub }
const listeners = new Set()     // (connected: boolean) => void

const notify = (connected) => listeners.forEach((l) => l(connected))

function attach(entry) {
  entry.sub = client.subscribe(entry.topic, (m) => entry.handler(JSON.parse(m.body)))
}

function ensure() {
  if (client) return client
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  client = new Client({
    brokerURL: `${proto}://${location.host}/ws`,
    reconnectDelay: 3000,
    onConnect: () => { subscriptions.forEach(attach); notify(true) },
    onWebSocketClose: () => { subscriptions.forEach((e) => { e.sub = null }); notify(false) }
  })
  client.activate()
  return client
}

export function subscribe(topic, handler) {
  const c = ensure()
  const entry = { topic, handler, sub: null }
  subscriptions.add(entry)
  if (c.connected) attach(entry) // otherwise onConnect attaches it
  return () => {
    subscriptions.delete(entry)
    try { entry.sub?.unsubscribe() } catch { /* connection already gone */ }
  }
}

/** Calls listener(true | false) on every real STOMP connect / disconnect, and once right away with the current state. */
export function onConnectionChange(listener) {
  const c = ensure()
  listeners.add(listener)
  listener(c.connected)
  return () => { listeners.delete(listener) }
}
