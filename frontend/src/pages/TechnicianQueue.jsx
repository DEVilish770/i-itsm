import { api } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { firstName, greeting, plural } from '../lib/words.js'
import { EmptyState, Hero, IncidentRow, Page, Section, Stat, useLoad, useNow } from '../components/ui.jsx'

export default function TechnicianQueue() {
  const { user } = useAuth()
  const now = useNow(30000)
  const { data } = useLoad(() => api('/api/incidents/queue'))
  const { data: techs } = useLoad(() => api('/api/technicians'))
  const me = (techs || []).find((t) => t.id === user.id)
  const list = data || []
  const need = list.filter((i) => i.status === 'ASSIGNED').length
  const risk = list.filter((i) => i.slaStatus === 'AT_RISK' || i.slaStatus === 'VIOLATED').length
  const full = me && me.activeWorkload >= me.maxWorkload

  const line = !data ? 'Checking your queue…'
    : list.length === 0 ? 'All quiet. Nothing needs you right now.'
      : `${plural(list.length, 'incident')} on your plate.${need ? ` ${need} waiting for you to acknowledge.` : ''}${risk ? ` ${risk} running out of time.` : ''}`

  return (
    <Page hero={<Hero title={`${greeting()}, ${firstName(user)}.`}><span>{line}</span></Hero>}>
      <div className="figures four">
        <Stat label="Active" value={me?.activeWorkload ?? list.length} title="Active incidents" />
        <Stat label="Capacity" value={me ? `${me.activeWorkload} / ${me.maxWorkload}` : '—'} tone={full ? 'urgent' : ''} title="Active workload / maximum workload" />
        <Stat label="Waiting for you" value={need} tone={need ? 'high' : ''} title="Awaiting acknowledgement (ASSIGNED)" />
        <Stat label="At risk or overdue" value={risk} tone={risk ? 'urgent' : ''} title="SLA at risk / breached" />
      </div>
      {me && (
        <p className="quiet me-line">
          Skills: {(me.skills || []).join(', ') || 'none yet'} · {me.experienceYears} {me.experienceYears === 1 ? 'year' : 'years'} of experience{me.teamLead ? ' · Team lead' : ''}
        </p>
      )}
      <Section title="Your queue" note="Most urgent first">
        {data && list.length === 0 && <EmptyState>Nothing needs you right now. New assignments appear here and in the bell.</EmptyState>}
        <div className="rows">{list.map((i, idx) => <IncidentRow key={i.id} i={i} index={idx} now={now} />)}</div>
      </Section>
    </Page>
  )
}
