import { useState } from 'react'
import { api, fmt } from '../lib/api.js'
import { sentence } from '../lib/words.js'
import { EmptyState, Page, Tabs, Tag, useLoad } from '../components/ui.jsx'

export default function AdminAudit() {
  const [tab, setTab] = useState('audit')
  const { data: audit } = useLoad(() => api('/api/admin/audit?size=100'), [tab])
  const { data: notes } = useLoad(() => api('/api/admin/notifications'), [tab])
  const { data: outbox } = useLoad(() => api('/api/admin/email-outbox'), [tab])
  return (
    <Page intro="Every change, who made it and when. Plus every notification sent.">
      <Tabs value={tab} onChange={setTab} label="Audit views"
        options={[['audit', 'Audit log'], ['notes', 'All notifications'], ['outbox', 'Email outbox (simulated)']]} />

      {tab === 'audit' && (
        <div className="table-scroll" key="audit">
          {audit && !audit.content?.length && <EmptyState>Nothing has happened yet.</EmptyState>}
          {audit?.content?.length > 0 && (
            <table className="table">
              <thead><tr><th>Time</th><th>Incident</th><th>Who</th><th>What</th><th>Change</th><th>Details</th></tr></thead>
              <tbody>
                {audit.content.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap quiet">{fmt.dt(a.timestamp)}</td>
                    <td className="mono nowrap">{a.incidentNumber}</td>
                    <td>{a.actor}</td>
                    <td title={a.action}>{sentence(a.action)}</td>
                    <td className="tech nowrap">{a.previousState !== a.newState ? `${a.previousState} → ${a.newState}` : a.newState}</td>
                    <td>{a.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === 'notes' && (
        <div className="table-scroll" key="notes">
          {notes && !notes.length && <EmptyState>No notifications sent yet.</EmptyState>}
          {notes?.length > 0 && (
            <table className="table">
              <thead><tr><th>Time</th><th>Channel</th><th>To</th><th>Subject</th><th>Event</th><th>Read</th></tr></thead>
              <tbody>
                {notes.map((n) => (
                  <tr key={n.id}>
                    <td className="nowrap quiet">{fmt.dt(n.sentAt)}</td>
                    <td className="tech">{n.channel}</td>
                    <td>{n.recipient}</td>
                    <td>{n.subject}{n.plainText && <div className="quiet">{n.plainText} <Tag source={n.textSource} /></div>}</td>
                    <td className="tech">{n.eventType}</td>
                    <td>{n.read ? 'Read' : <span className="quiet">—</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === 'outbox' && (
        <div key="outbox">
          {outbox && outbox.length === 0 && <EmptyState>No emails sent yet.</EmptyState>}
          <div className="outbox">{(outbox || []).map((l, i) => <div key={i} className="outbox-line mono">{l}</div>)}</div>
        </div>
      )}
    </Page>
  )
}
