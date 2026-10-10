import type { Assessment } from '../types'

// Readiness gauges: factual counts of stated requirements against declared data.
// Deliberately NOT a probability of winning or an eligibility score.

export interface Readiness {
  hoursRequired: number
  hoursCovered: number
  hoursConditional: boolean
  locations: { area: string; required: number; covered: number }[]
  met: number
  metConditional: number
  total: number
  mandatoryTotal: number
  mandatoryOpen: number
}

export function measure(a: Assessment): Readiness {
  const locations = a.findings.filter(f => f.kind === 'CAPACITY').flatMap(f => {
    const req = a.extraction.requirements.find(r => r.id === f.requirementId)
    if (!req?.area || !req.weeklyHours) return []
    const got = f.members.reduce((sum, m) => sum + (m.hoursCounted ?? 0), 0)
    return [{ area: req.area, required: req.weeklyHours, covered: Math.min(req.weeklyHours, got) }]
  })
  const mandatory = a.findings.filter(f => f.category === 'MANDATORY_ELIGIBILITY')
  return {
    hoursRequired: locations.reduce((s, l) => s + l.required, 0),
    hoursCovered: locations.reduce((s, l) => s + l.covered, 0),
    hoursConditional: a.findings.some(f => f.kind === 'CAPACITY' && f.conditional),
    locations,
    met: a.findings.filter(f => f.status === 'MATCH').length,
    metConditional: a.findings.filter(f => f.status === 'MATCH' && f.conditional).length,
    total: a.findings.length,
    mandatoryTotal: mandatory.length,
    mandatoryOpen: mandatory.filter(f => f.status !== 'MATCH' || f.conditional).length,
  }
}

function Ring({ value, tone, dashed }: { value: number; tone: string; dashed?: boolean }) {
  const r = 34
  const c = 2 * Math.PI * r
  const v = Math.max(0, Math.min(1, value))
  return (
    <svg className="gauge-ring" width="92" height="92" viewBox="0 0 92 92" aria-hidden="true">
      <circle cx="46" cy="46" r={r} fill="none" stroke="var(--rule-strong)" strokeWidth="8" opacity="0.35" />
      {Array.from({ length: 12 }, (_, i) => (
        <line key={i} x1="46" y1="3" x2="46" y2="8" stroke="var(--ink-3)" strokeWidth="1" transform={`rotate(${i * 30} 46 46)`} />
      ))}
      <circle className="gauge-arc" cx="46" cy="46" r={r} fill="none" stroke={tone} strokeWidth="8"
        strokeDasharray={`${c * v} ${c}`} transform="rotate(-90 46 46)" style={{ ['--c' as string]: `${c}` }} />
      {dashed && <circle cx="46" cy="46" r="43" fill="none" stroke={tone} strokeWidth="1.5" strokeDasharray="4 4" />}
    </svg>
  )
}

function pct(n: number, d: number) {
  return d === 0 ? 0 : Math.round((n / d) * 100)
}

export function ReadinessGauges({ a, before }: { a: Assessment; before?: Assessment | null }) {
  const now = measure(a)
  const was = before ? measure(before) : null
  const hoursPct = pct(now.hoursCovered, now.hoursRequired)
  const hoursTone = hoursPct >= 100 ? (now.hoursConditional ? 'var(--brass)' : 'var(--kelp)') : 'var(--signal)'
  const mandatoryDone = now.mandatoryTotal - now.mandatoryOpen

  return (
    <section className="gauges" aria-label="Readiness gauges">
      {now.hoursRequired > 0 && (
        <div className="gauge">
          <div className="gauge-dial">
            <Ring value={now.hoursCovered / now.hoursRequired} tone={hoursTone} dashed={hoursPct >= 100 && now.hoursConditional} />
            <span className="gauge-value">{hoursPct}<small>%</small></span>
          </div>
          <div className="gauge-body">
            <div className="label">Delivery hours covered</div>
            <div className="gauge-main">{now.hoursCovered} of {now.hoursRequired} h/week{now.hoursConditional && hoursPct >= 100 ? ' · conditional' : ''}</div>
            <div className="gauge-sub">{now.locations.map(l => `${l.area} ${l.covered}/${l.required}`).join(' · ')}</div>
            {was && <Delta from={`${pct(was.hoursCovered, was.hoursRequired)}%`} to={`${hoursPct}%`} better={hoursPct > pct(was.hoursCovered, was.hoursRequired)} />}
          </div>
        </div>
      )}
      <div className="gauge">
        <div className="gauge-dial">
          <Ring value={now.met / Math.max(1, now.total)} tone="var(--ink)" />
          <span className="gauge-value">{now.met}<small>/{now.total}</small></span>
        </div>
        <div className="gauge-body">
          <div className="label">Requirements met</div>
          <div className="gauge-main">{now.met} of {now.total} assessed{now.metConditional ? ` · ${now.metConditional} conditional` : ''}</div>
          <div className="gauge-sub">Includes scored criteria; unknown is not counted as met</div>
          {was && <Delta from={`${was.met}/${was.total}`} to={`${now.met}/${now.total}`} better={now.met > was.met} />}
        </div>
      </div>
      <div className="gauge">
        <div className="gauge-dial">
          <Ring value={mandatoryDone / Math.max(1, now.mandatoryTotal)} tone={now.mandatoryOpen ? 'var(--brass)' : 'var(--kelp)'} />
          <span className="gauge-value">{now.mandatoryOpen}<small> open</small></span>
        </div>
        <div className="gauge-body">
          <div className="label">Mandatory conditions open</div>
          <div className="gauge-main">{now.mandatoryOpen} of {now.mandatoryTotal} still need action</div>
          <div className="gauge-sub">Evidence, credentials and buyer approval are never filled by a partner</div>
          {was && <Delta from={String(was.mandatoryOpen)} to={String(now.mandatoryOpen)} better={now.mandatoryOpen < was.mandatoryOpen} />}
        </div>
      </div>
      <p className="gauges-note">Gauges count stated requirements against declared profile data. They are not a probability of winning or an eligibility score.</p>
    </section>
  )
}

function Delta({ from, to, better }: { from: string; to: string; better: boolean }) {
  const same = from === to
  return (
    <div className={`gauge-delta ${same ? 'same' : better ? 'up' : 'down'}`}>
      Before {from} → after {to}{same ? ' (unchanged)' : ''}
    </div>
  )
}
