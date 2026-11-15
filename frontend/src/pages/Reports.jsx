import { api, fmt } from '../lib/api.js'
import { reportSummary } from '../lib/ai.jsx'
import { PRIORITIES, PRIORITY, plural } from '../lib/words.js'
import { Bars, Page, Section, Stat, Tag, TypingDots, useLoad } from '../components/ui.jsx'

/** A rules-written summary from the numbers, for when the summary endpoint can't be reached. */
function rulesSummary(ov, perf) {
  if (!ov) return null
  const s = []
  s.push(ov.totalIncidents === 0 ? 'Nothing has been reported yet.' : `${plural(ov.totalIncidents, 'incident')} reported so far, and ${ov.openIncidents} still open.`)
  if (ov.criticalIncidents) s.push(`${ov.criticalIncidents} urgent ${ov.criticalIncidents === 1 ? 'one is' : 'ones are'} being worked on.`)
  s.push(ov.slaViolations ? `${ov.slaViolations} missed ${ov.slaViolations === 1 ? 'its deadline' : 'their deadlines'}.` : 'No deadlines have been missed.')
  if (perf?.resolvedCount) s.push(`${perf.slaCompliancePct}% of fixed incidents met their deadline, and a fix took ${fmt.mins(perf.meanTimeToResolveMinutes)} on average.`)
  return s.join(' ')
}

export default function Reports() {
  const { data: ov } = useLoad(() => api('/api/admin/reports/overview'))
  const { data: perf } = useLoad(() => api('/api/admin/reports/performance'))
  const { data: svc } = useLoad(() => api('/api/admin/reports/services'))
  const { data: cat } = useLoad(() => api('/api/admin/reports/categories'))
  const { data: load } = useLoad(() => api('/api/admin/reports/workload'))
  const { data: summary, loading: writing } = useLoad(() => reportSummary())

  const plain = summary || (!writing && ov ? { source: 'RULES', text: rulesSummary(ov, perf), generatedAt: null } : null)
  const byPriority = ov ? PRIORITIES.map((p) => ({ key: p, label: `${PRIORITY[p].word} (${p})`, value: ov.byPriority?.[p] ?? 0, max: Math.max(1, ov.totalIncidents), tone: PRIORITY[p].tone })) : []

  return (
    <Page intro="How IT is doing: volumes, speed and deadlines.">
      <section className="plain-words" aria-live="polite">
        <div className="label">In plain words {plain && <Tag source={plain.source} />}</div>
        {!plain && <p className="plain-text quiet"><TypingDots label="Writing the summary" /> Writing a summary…</p>}
        {plain && <p className="plain-text">{plain.text}</p>}
        {plain?.generatedAt && <p className="quiet">Written {fmt.ago(plain.generatedAt)}</p>}
      </section>

      <div className="figures five">
        <Stat label="All incidents" value={ov?.totalIncidents} title="Total incidents" />
        <Stat label="Open" value={ov?.openIncidents} />
        <Stat label="Resolved" value={ov?.resolvedIncidents} title="Resolved, verified or closed" />
        <Stat label="Overdue" value={ov?.slaViolations} tone={ov?.slaViolations ? 'urgent' : ''} title="SLA violations" />
        <Stat label="Met their deadline" value={perf ? `${perf.slaCompliancePct}%` : null} tone={perf && perf.slaCompliancePct < 90 ? 'high' : ''} title="SLA compliance over resolved incidents" />
      </div>
      <div className="figures four">
        <Stat label="Time to respond" value={perf ? fmt.mins(perf.meanTimeToAcknowledgeMinutes) : null} title="Mean time to acknowledge (MTTA)" />
        <Stat label="Time to fix" value={perf ? fmt.mins(perf.meanTimeToResolveMinutes) : null} title="Mean time to resolve (MTTR)" />
        <Stat label="Average rating" value={perf ? `${perf.averageFeedbackRating} / 5` : null} title="Average feedback rating" />
        <Stat label="Urgent and open" value={ov?.criticalIncidents} tone={ov?.criticalIncidents ? 'urgent' : ''} title="Critical open (P1)" />
      </div>

      <div className="duo">
        <Section title="By service"><Bars rows={(svc || []).map((r) => ({ label: r.service, value: r.incidents }))} empty="No incidents yet." /></Section>
        <Section title="By category"><Bars rows={(cat || []).map((r) => ({ label: r.category, value: r.incidents }))} empty="No incidents yet." /></Section>
        <Section title="Who's working on what" note="Active workload"><Bars rows={(load || []).map((r) => ({ label: r.technician, value: r.activeIncidents }))} empty="No one has active work right now." /></Section>
        <Section title="By priority"><Bars rows={byPriority} /></Section>
      </div>
    </Page>
  )
}
