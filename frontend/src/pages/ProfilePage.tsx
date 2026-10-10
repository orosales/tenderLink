import { useEffect, useState } from 'react'
import { api } from '../api'
import { useApp, useLoad } from '../context'
import { NovaScotiaChart, type ChartFlag } from '../components/NovaScotiaChart'
import { ErrorNotice, Loading, Stamp, Unknown } from '../components/Marks'
import { SignalFlag } from '../components/Flag'
import { fmtDate } from '../components/format'
import type { CapacityEntry, CompanyProfile, EvidenceStatus, Qualification } from '../types'

const EVIDENCE_LABEL: Record<EvidenceStatus, string> = {
  EVIDENCED: 'Evidence on file',
  DECLARED_NO_EVIDENCE: 'Declared, evidence missing',
  UNKNOWN: 'Unknown',
}

export function ProfilePage() {
  const { activeId, active, flag, refreshCompanies, toast, companies, version, status } = useApp()
  const { data, setData, error, loading, reload } = useLoad(
    () => (activeId ? api<CompanyProfile>(`/companies/${activeId}`) : Promise.resolve(null)),
    [activeId, version],
  )
  const [draft, setDraft] = useState<CompanyProfile | null>(null)
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState<unknown>(null)

  useEffect(() => {
    setDraft(data)
    setSaveError(null)
  }, [data])

  if (!activeId) return <Loading />
  if (error) return <ErrorNotice error={error} onRetry={reload} />
  if (loading || !draft || !data) return <Loading label="Unrolling the profile…" />

  const dirty = JSON.stringify(stripMeta(draft)) !== JSON.stringify(stripMeta(data))
  const confirmed = !!data.profileConfirmedAt

  async function save() {
    if (!draft) return
    setBusy(true)
    setSaveError(null)
    try {
      const saved = await api<CompanyProfile>(`/companies/${draft.id}`, {
        method: 'PUT',
        json: {
          name: draft.name, contactName: draft.contactName, services: draft.services, specializations: draft.specializations,
          serviceAreas: draft.serviceAreas, capacity: draft.capacity, experience: draft.experience,
          qualifications: draft.qualifications, evidenceSummary: draft.evidenceSummary,
          partnershipPreference: draft.partnershipPreference, openToPartnerships: draft.openToPartnerships,
        },
      })
      setData(saved)
      await refreshCompanies()
      toast(saved.profileRevision !== data!.profileRevision ? 'Draft saved. Confirmation cleared until you review it.' : 'No changes to save.')
    } catch (e) {
      setSaveError(e)
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    setBusy(true)
    try {
      const saved = await api<CompanyProfile>(`/companies/${data!.id}/confirm`, { method: 'POST' })
      setData(saved)
      await refreshCompanies()
      toast('Profile confirmed. Assessments and partner search will use this revision.')
    } catch (e) {
      setSaveError(e)
    } finally {
      setBusy(false)
    }
  }

  const set = <K extends keyof CompanyProfile>(k: K, v: CompanyProfile[K]) => setDraft({ ...draft, [k]: v })
  const flags: ChartFlag[] = companies.filter(c => c.demo).flatMap(c =>
    c.serviceAreas.map(a => ({ area: a, flag: flag(c.id), label: c.name.split(' ')[0] })))

  return (
    <>
      <header className="page-head">
        <div>
          <div className="eyebrow">§01 · Company profile</div>
          <h1 className="row" style={{ gap: 18 }}>
            <SignalFlag kind={flag(activeId)} size={58} />
            <span>{data.name}</span>
          </h1>
          <p className="lede">
            What you declare here is the only basis for gap assessments and partner matching. Missing information stays{' '}
            <Unknown /> and is never treated as a failed requirement.
          </p>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          {!(confirmed && !dirty) && <Stamp kind="fixture" big>Draft · awaiting confirmation</Stamp>}
          <span className="mono tiny muted">Last updated {fmtDate(data.updatedAt)}</span>
        </div>
      </header>

      {!confirmed && (
        <div className="notice warn" style={{ marginBottom: 18 }}>
          <span className="glyph">◆</span>
          <div>This profile changed since it was last confirmed. Review it and confirm before running assessments{data.openToPartnerships ? ' or appearing in partner search' : ''}.</div>
        </div>
      )}
      {saveError != null && <div style={{ marginBottom: 18 }}><ErrorNotice error={saveError} /></div>}

      <div className="grid sidebar">
        <div className="stack" style={{ gap: 22 }}>
          <section className="card">
            <div className="card-head"><h2>Identity</h2><span className="label">Visible to partners: name only</span></div>
            <div className="grid two">
              <div className="field"><label htmlFor="name">Company name</label>
                <input id="name" type="text" value={draft.name} onChange={e => set('name', e.target.value)} /></div>
              <div className="field"><label htmlFor="contact">Contact name</label>
                <input id="contact" type="text" value={draft.contactName ?? ''} onChange={e => set('contactName', e.target.value)} placeholder="unknown" /></div>
            </div>
          </section>

          <section className="card">
            <div className="card-head"><h2>Services &amp; reach</h2></div>
            <div className="grid two">
              <ChipField label="Services" values={draft.services} onChange={v => set('services', v)} />
              <ChipField label="Specializations" values={draft.specializations} onChange={v => set('specializations', v)} />
            </div>
            <div className="spacer" />
            <ChipField label="Service areas (cities / sites)" values={draft.serviceAreas} onChange={v => set('serviceAreas', v)} />
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Weekly capacity by location</h2>
              <span className="label">Hours never transfer between locations</span>
            </div>
            <CapacityEditor rows={draft.capacity} onChange={v => set('capacity', v)} />
          </section>

          <section className="card">
            <div className="card-head"><h2>Experience &amp; qualifications</h2></div>
            <div className="field"><label htmlFor="exp">Experience</label>
              <textarea id="exp" value={draft.experience ?? ''} onChange={e => set('experience', e.target.value)} placeholder="unknown" /></div>
            <div className="spacer" />
            <QualificationEditor rows={draft.qualifications} onChange={v => set('qualifications', v)} />
            <div className="spacer" />
            <div className="field"><label htmlFor="evid">Evidence descriptions (private, never shown in partner search)</label>
              <textarea id="evid" value={draft.evidenceSummary ?? ''} onChange={e => set('evidenceSummary', e.target.value)} placeholder="unknown" /></div>
          </section>

          <section className="card">
            <div className="card-head"><h2>Partnering</h2></div>
            <label className="check">
              <input type="checkbox" checked={draft.openToPartnerships} onChange={e => set('openToPartnerships', e.target.checked)} />
              <span><b>Open to partnerships.</b> Only opted-in, confirmed profiles can appear as partner candidates for other companies.</span>
            </label>
            <div className="spacer" />
            <div className="field"><label htmlFor="pref">Partnership preference</label>
              <textarea id="pref" value={draft.partnershipPreference ?? ''} onChange={e => set('partnershipPreference', e.target.value)} placeholder="unknown" /></div>
          </section>

          <div className="card sunk row between save-bar">
            <span className="small muted save-note">{dirty ? 'Unsaved changes. Saving creates a new revision and clears confirmation.' : confirmed ? `Confirmed ${fmtDate(data.profileConfirmedAt)}` : 'Saved draft, not yet confirmed.'}</span>
            <div className="row">
              <button className="btn ghost" disabled={!dirty || busy} onClick={() => setDraft(data)}>Discard</button>
              <button className="btn ghost" disabled={!dirty || busy} onClick={save}>Save draft</button>
              <button className="btn kelp" disabled={dirty || confirmed || busy} onClick={confirm} title={dirty ? 'Save the draft first' : undefined}>
                ✓ Confirm profile
              </button>
            </div>
          </div>
        </div>

        <aside className="stack" style={{ gap: 22, position: 'sticky', top: 56 }}>
          <NovaScotiaChart flags={flags} caption="Declared service areas of the demo companies" />
          <div className="card flat">
            <div className="label" style={{ marginBottom: 10 }}>Presenter journey</div>
            <ol className="journey">
              <li>Hoist <b>Harbour Clean</b> and show its confirmed profile.</li>
              <li>Refresh public tenders, then open the sample tender.</li>
              <li>Run the assessment: Truro gap and missing evidence.</li>
              <li>See why Fundy Clean is a potential fit.</li>
              <li>Approve and invite.</li>
              <li>Hoist <b>Fundy Clean</b>: accept interest, confirm contribution.</li>
              <li>Hoist Harbour Clean again and confirm.</li>
              <li>Reassess: Truro covered, conditions still open.</li>
            </ol>
          </div>
          {status?.demoMode && <DemoReset />}
          {active && <p className="tiny muted mono">Company id {active.id}</p>}
        </aside>
      </div>
    </>
  )
}

function stripMeta(p: CompanyProfile) {
  const { updatedAt, profileConfirmedAt, profileRevision, ownProfile, demo, ...rest } = p
  void updatedAt; void profileConfirmedAt; void profileRevision; void ownProfile; void demo
  return rest
}

function ChipField({ label, values, onChange }: { label: string; values: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState('')
  const add = () => {
    const t = text.trim()
    if (t && !values.some(v => v.toLowerCase() === t.toLowerCase())) onChange([...values, t])
    setText('')
  }
  return (
    <div className="field">
      <span className="label">{label}</span>
      <div className="chips">
        {values.length === 0 && <Unknown />}
        {values.map(v => (
          <span className="chip" key={v}>{v}<button aria-label={`Remove ${v}`} onClick={() => onChange(values.filter(x => x !== v))}>×</button></span>
        ))}
      </div>
      <div className="chip-input">
        <input type="text" value={text} placeholder="Add…" aria-label={`Add to ${label}`}
          onChange={e => setText(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); add() } }} />
        <button className="btn small ghost" onClick={add} disabled={!text.trim()}>Add</button>
      </div>
    </div>
  )
}

function CapacityEditor({ rows, onChange }: { rows: CapacityEntry[]; onChange: (r: CapacityEntry[]) => void }) {
  const update = (i: number, patch: Partial<CapacityEntry>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  return (
    <>
      <table className="ledger edit">
        <thead><tr><th>Location</th><th>Service</th><th style={{ width: 130 }}>Hours / week</th><th style={{ width: 40 }} /></tr></thead>
        <tbody>
          {rows.length === 0 && <tr><td colSpan={4}><Unknown>No capacity declared: coverage will be assessed as unknown</Unknown></td></tr>}
          {rows.map((r, i) => (
            <tr key={i}>
              <td><input type="text" aria-label="Location" placeholder="Location" value={r.area} onChange={e => update(i, { area: e.target.value })} /></td>
              <td><input type="text" aria-label="Service" placeholder="Service" value={r.service} onChange={e => update(i, { service: e.target.value })} /></td>
              <td><input type="number" min={0} aria-label="Weekly hours" placeholder="Hours / week" value={r.weeklyHours}
                onChange={e => update(i, { weeklyHours: Math.max(0, Number(e.target.value) || 0) })} /></td>
              <td><button className="link-btn row-del" aria-label="Remove row" onClick={() => onChange(rows.filter((_, j) => j !== i))}>×</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="spacer" style={{ height: 10 }} />
      <button className="btn small ghost" onClick={() => onChange([...rows, { area: '', service: 'Commercial office cleaning', weeklyHours: 0 }])}>+ Add location</button>
    </>
  )
}

function QualificationEditor({ rows, onChange }: { rows: Qualification[]; onChange: (r: Qualification[]) => void }) {
  const update = (i: number, patch: Partial<Qualification>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  return (
    <div className="field">
      <span className="label">Qualifications</span>
      <table className="ledger edit">
        <thead><tr><th>Qualification</th><th style={{ width: 210 }}>Status</th><th>Private evidence note</th><th style={{ width: 40 }} /></tr></thead>
        <tbody>
          {rows.length === 0 && <tr><td colSpan={4}><Unknown>None declared (unknown, not assumed missing)</Unknown></td></tr>}
          {rows.map((q, i) => (
            <tr key={i}>
              <td><input type="text" aria-label="Qualification name" placeholder="Qualification" value={q.name} onChange={e => update(i, { name: e.target.value })} /></td>
              <td>
                <select aria-label="Evidence status" value={q.status} onChange={e => update(i, { status: e.target.value as EvidenceStatus })}>
                  {(Object.keys(EVIDENCE_LABEL) as EvidenceStatus[]).map(s => <option key={s} value={s}>{EVIDENCE_LABEL[s]}</option>)}
                </select>
              </td>
              <td><input type="text" aria-label="Evidence note" value={q.evidenceNote ?? ''} placeholder="private" onChange={e => update(i, { evidenceNote: e.target.value })} /></td>
              <td><button className="link-btn row-del" aria-label="Remove qualification" onClick={() => onChange(rows.filter((_, j) => j !== i))}>×</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <div><button className="btn small ghost" onClick={() => onChange([...rows, { name: '', status: 'UNKNOWN', evidenceNote: null }])}>+ Add qualification</button></div>
    </div>
  )
}

function DemoReset() {
  const { toast, refreshCompanies, setActive, activeId } = useApp()
  const [armed, setArmed] = useState(false)
  const [busy, setBusy] = useState(false)
  async function reset() {
    setBusy(true)
    try {
      await api('/demo/reset', { method: 'POST', json: { confirm: 'RESET DEMO' } })
      await refreshCompanies()
      if (activeId) setActive(activeId)
      toast('Demo records removed and reseeded. Imported notices untouched.')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Reset failed', 'error')
    } finally {
      setBusy(false)
      setArmed(false)
    }
  }
  return (
    <div className="card flat">
      <div className="label" style={{ marginBottom: 8 }}>Local demo reset</div>
      <p className="small muted" style={{ marginTop: 0 }}>Removes only the sample-scenario records (companies, tender, their invitations and assessments) and reseeds them. Works from localhost only.</p>
      {armed
        ? <div className="row"><button className="btn signal small" disabled={busy} onClick={reset}>Yes, reset demo data</button><button className="btn ghost small" onClick={() => setArmed(false)}>Cancel</button></div>
        : <button className="btn ghost small" onClick={() => setArmed(true)}>Reset demo data…</button>}
    </div>
  )
}
