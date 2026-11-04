import { Client } from '@stomp/stompjs'

// One shared STOMP client over native WebSocket. Callers subscribe to topics and get an unsubscribe fn.
let client = null
const pending = []

function ensure() {
  if (client) return client
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  client = new Client({
    brokerURL: `${proto}://${location.host}/ws`,
    reconnectDelay: 3000,
    onConnect: () => { pending.splice(0).forEach((f) => f()) }
  })
  client.activate()
  return client
}

export function subscribe(topic, handler) {
  const c = ensure()
  let sub = null
  const doSub = () => { sub = c.subscribe(topic, (m) => handler(JSON.parse(m.body))) }
  if (c.connected) doSub(); else pending.push(doSub)
  return () => { try { sub?.unsubscribe() } catch { /* ignore */ } }
}
