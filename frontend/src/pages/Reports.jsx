import { api, fmt } from '../lib/api.js'
import { Stat, useLoad } from '../components/ui.jsx'

export default function Reports() {
  const { data: ov } = useLoad(() => api('/api/admin/reports/overview'))
  const { data: perf } = useLoad(() => api('/api/admin/reports/performance'))
  const { data: svc } = useLoad(() => api('/api/admin/reports/services'))
  const { data: cat } = useLoad(() => api('/api/admin/reports/categories'))
  const { data: load } = useLoad(() => api('/api/admin/reports/workload'))
  const max = (arr, k) => Math.max(1, ...(arr || []).map((x) => x[k]))
  const Bars = ({ rows, label, value }) => (rows || []).map((r) => <div key={r[label]} className="bar"><span style={{ width: 150 }} className="small">{r[label]}</span><div className="track"><div style={{ width: `${(r[value] / max(rows, value)) * 100}%` }} /></div><span className="small muted">{r[value]}</span></div>)
  return (
    <div className="stack">
      <h1>Reports &amp; analytics</h1>
      <div className="grid cols-5">
        <Stat label="Total" value={ov?.totalIncidents} /><Stat label="Open" value={ov?.openIncidents} />
        <Stat label="Resolved" value={ov?.resolvedIncidents} tone="ok" /><Stat label="SLA violations" value={ov?.slaViolations} tone={ov?.slaViolations ? 'bad' : ''} />
        <Stat label="SLA compliance" value={perf ? `${perf.slaCompliancePct}%` : '—'} tone={perf && perf.slaCompliancePct < 90 ? 'warn' : 'ok'} />
      </div>
      <div className="grid cols-4">
        <Stat label="Mean time to acknowledge" value={perf ? fmt.mins(perf.meanTimeToAcknowledgeMinutes) : '—'} />
        <Stat label="Mean time to resolve" value={perf ? fmt.mins(perf.meanTimeToResolveMinutes) : '—'} />
        <Stat label="Avg. feedback rating" value={perf ? `${perf.averageFeedbackRating} / 5` : '—'} />
        <Stat label="Critical open (P1)" value={ov?.criticalIncidents} tone={ov?.criticalIncidents ? 'bad' : ''} />
      </div>
      <div className="grid cols-2">
        <div className="card"><h3>Incidents by service</h3><Bars rows={svc} label="service" value="incidents" /></div>
        <div className="card"><h3>Incidents by category</h3><Bars rows={cat} label="category" value="incidents" /></div>
        <div className="card"><h3>Technician workload (active)</h3><Bars rows={load} label="technician" value="activeIncidents" />{!load?.length && <p className="muted small">No active assignments.</p>}</div>
        <div className="card"><h3>By priority</h3>{ov && Object.entries(ov.byPriority).map(([k, v]) => <div key={k} className="bar"><span style={{ width: 150 }}><span className={`badge ${k}`}>{k}</span></span><div className="track"><div style={{ width: `${ov.totalIncidents ? (v / ov.totalIncidents) * 100 : 0}%` }} /></div><span className="small muted">{v}</span></div>)}</div>
      </div>
    </div>
  )
}
