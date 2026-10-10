import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { api, ApiError } from '../api'
import { go, useApp, useLoad } from '../context'
import { SignalFlag } from '../components/Flag'
import { ErrorNotice, Loading, ModeStamp, StatusMark, TenderStamp, Unknown } from '../components/Marks'
import { NovaScotiaChart, type ChartSite } from '../components/NovaScotiaChart'
import { ReadinessGauges } from '../components/ReadinessGauges'
import { APPLIES_LABEL, CATEGORY_LABEL, PERMISSION_LABEL, fmtDate } from '../components/format'
import type { AppliesTo, Assessment, Category, Finding, Kind, ProposedContribution, Recommendation, Recommendations, TenderDetail } from '../types'

export function chartSites(a: Assessment, flag: (id?: string) => ReturnType<ReturnType<typeof useApp>['flag']>): ChartSite[] {
  return a.findings.filter(f => f.kind === 'CAPACITY').flatMap(f => {
    const req = a.extraction.requirements.find(r => r.id === f.requirementId)
    if (!req?.area || !req.weeklyHours) return []
    return [{
      area: req.area,
      required: req.weeklyHours,
      status: f.status,
      conditional: f.conditional,
      segments: f.members.map(m => ({ hours: m.hoursCounted ?? 0, flag: flag(m.companyId), label: m.companyName.replace(' Demo', '') })),
    }]
  })
}

export function AssessmentPage({ tenderId }: { tenderId: string }) {
  const { activeId, version } = useApp()
  const tender = useLoad(() => api<TenderDetail>(`/tenders/${tenderId}`), [tenderId])
  const latest = useLoad(() => api<Assessment | null>(`/tenders/${tenderId}/assessments/latest`), [tenderId, activeId, version])

  if (latest.error) return <ErrorNotice error={latest.error} onRetry={latest.reload} />
  if (latest.loading || tender.loading) return <Loading label="Plotting the assessment…" />
  const t = tender.data?.summary

  if (!latest.data) {
    return (
      <>
        <header className="page-head"><div>
          <a className="link-btn" href={`#/tenders/${tenderId}`}>← Tender</a>
          <div className="eyebrow" style={{ marginTop: 14 }}>§03 · Assessment</div>
          <h1>{t?.title ?? 'Tender'}</h1></div></header>
        <div className="empty">
          <h3>Not assessed yet for the active company</h3>
          <p className="small">Run the gap assessment from the tender detail to compare the document's requirements with your confirmed profile.</p>
          <a className="btn" href={`#/tenders/${tenderId}`}>Go to tender</a>
        </div>
      </>
    )
  }

  return <AssessmentScreen a={latest.data} onChange={latest.setData} reload={latest.reload} />
}

function AssessmentScreen({ a, onChange, reload }: { a: Assessment; onChange: (a: Assessment) => void; reload: () => void }) {
  const { flag, version } = useApp()
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<unknown>(null)
  // Deterministic partner shortlist, used to point each capacity gap at a candidate (no AI call).
  const recs = useLoad(() => api<Recommendations>(`/tenders/${a.tenderId}/partner-recommendations?aiExplanation=false`), [a.tenderId, a.id, a.stale, version])

  async function reassess() {
    setBusy(true)
    setErr(null)
    try {
      onChange(await api<Assessment>(`/tenders/${a.tenderId}/assessments`, { method: 'POST', json: { mode: a.mode } }))
    } catch (e) {
      setErr(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <header className="page-head">
        <div>
          <a className="link-btn" href={`#/tenders/${a.tenderId}`}>← Tender</a>
          <div className="eyebrow" style={{ marginTop: 14 }}>§03 · Gap assessment · {a.company.name}</div>
          <h1>{a.tenderTitle}</h1>
          <p className="lede">{a.extraction.scopeSummary ?? 'Scope not stated.'}</p>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          <TenderStamp demo={a.tenderDemo} />
          <ModeStamp mode={a.mode} />
          <span className="mono tiny muted">Assessed {fmtDate(a.createdAt)}</span>
        </div>
      </header>

      {a.stale && (
        <div className="notice stale" style={{ marginBottom: 18 }} role="alert">
          <span className="glyph">⟳</span>
          <div style={{ flex: 1 }}><b>Stale assessment.</b> {a.staleReasons.join(' ')}</div>
          <button className="btn small" onClick={reassess} disabled={busy}>{busy ? 'Reassessing…' : 'Reassess'}</button>
        </div>
      )}
      {err != null && <div style={{ marginBottom: 18 }}><ErrorNotice error={err} /></div>}

      <AssessmentBody a={a} onCorrected={onChange} recs={recs.data} />

      <div className="spacer" />
      <PartnerPanel a={a} key={a.id + String(a.stale)} onInvited={reload} flagOf={flag} />
    </>
  )
}

export function AssessmentBody({ a, onCorrected, readOnly, hideChart, hideGauges, recs }: { a: Assessment; onCorrected?: (a: Assessment) => void; readOnly?: boolean; hideChart?: boolean; hideGauges?: boolean; recs?: Recommendations | null }) {
  const { flag } = useApp()
  const ex = a.extraction
  const counts = a.findings.reduce<Record<string, number>>((m, f) => ({ ...m, [f.status]: (m[f.status] ?? 0) + 1 }), {})
  const sideCards = (
    <>
      <div className="card flat">
        <div className="card-head"><h3>Partnering clause</h3><span className={`status ${ex.partnershipPermission === 'PROHIBITED' ? 'GAP' : ex.partnershipPermission === 'UNKNOWN' ? 'UNKNOWN' : 'MISSING_EVIDENCE'}`}>{PERMISSION_LABEL[ex.partnershipPermission]}</span></div>
        {ex.partnershipArrangement && <div className="small"><b>Arrangement:</b> {ex.partnershipArrangement}</div>}
        {ex.partnershipExcerpt
          ? <blockquote className={`excerpt${ex.partnershipExcerptVerified ? '' : ' unverified'}`}>“{ex.partnershipExcerpt}”<cite>{ex.partnershipSourceReference ?? 'reference unknown'}{ex.partnershipExcerptVerified ? ' · verified in document' : ' · unsupported quote'}</cite></blockquote>
          : <Unknown>The document is silent on partnering</Unknown>}
        {ex.partnershipConditions.length > 0 && <ul className="small" style={{ paddingLeft: 18, marginBottom: 0 }}>{ex.partnershipConditions.map(c => <li key={c}>{c}</li>)}</ul>}
        {ex.jointVentureNote && <p className="tiny muted" style={{ marginBottom: 0 }}>{ex.jointVentureNote}</p>}
      </div>
      <div className="card flat">
        <div className="card-head"><h3>Source &amp; method</h3></div>
        <div className="small">{a.modeLabel}</div>
        <div className="tiny muted mono" style={{ marginTop: 6 }}>Documents: {a.documentSources.join(', ')}</div>
        <div className="tiny muted mono">Version {a.documentVersion.slice(0, 12)}…</div>
        {!a.documentComplete && <div className="notice warn small" style={{ marginTop: 10 }}><span className="glyph">◆</span><div><b>Document unavailable / assessment incomplete.</b> {ex.documentCompletenessNote}</div></div>}
        {a.limitations.length > 0 && <ul className="tiny muted" style={{ paddingLeft: 16 }}>{a.limitations.map(l => <li key={l}>{l}</li>)}</ul>}
      </div>
      {ex.deadlines.length > 0 && (
        <div className="card flat">
          <div className="card-head"><h3>Key dates</h3></div>
          {ex.deadlines.map(d => (
            <div key={d.label} className="small" style={{ marginBottom: 8 }}><b>{d.label}:</b> {d.value} <span className="mono tiny muted">{d.sourceReference}</span></div>
          ))}
        </div>
      )}
    </>
  )
  return (
    <>
      <section className={`verdict-band ${a.readiness}`} aria-label="Readiness reading">
        <div className="verdict-text">
          <span className="label">Readiness reading{a.combined ? ' · with confirmed partner' : ''}</span>
          <div className="verdict">{a.readinessLabel}</div>
          <div className="fine">A reading of stated requirements against declared data. Not a prediction of winning or a legal eligibility decision.</div>
        </div>
        <div className="soundings">
          {(['MATCH', 'GAP', 'MISSING_EVIDENCE', 'UNKNOWN'] as const).map(s => (
            <div key={s} className={`sounding ${s}`}><span className="counter">{counts[s] ?? 0}</span><StatusMark status={s} /></div>
          ))}
        </div>
      </section>
      {!hideGauges && <ReadinessGauges a={a} />}

      <div className="spacer" />
      {hideChart
        ? <div className="grid three">{sideCards}</div>
        : (
          <div className="grid sidebar">
            <NovaScotiaChart sites={chartSites(a, flag)} caption="Required versus declared weekly hours by delivery location" />
            <aside className="stack" style={{ gap: 18 }}>{sideCards}</aside>
          </div>
        )}

      <div className="spacer" />
      <div className="grid two">
        <section className="card">
          <div className="card-head"><h3>Action list</h3></div>
          {a.actions.length === 0 ? <p className="small">No open actions in assessed requirements.</p> : (
            <ol className="journey">{a.actions.map((x, i) => <li key={i}>{x.text}{x.requirementId && <span className="mono tiny muted"> ({x.requirementId})</span>}</li>)}</ol>
          )}
        </section>
        <section className="card flat">
          <div className="card-head"><h3>Unknowns &amp; warnings</h3></div>
          {a.warnings.length === 0 && ex.unknowns.length === 0 ? <p className="small muted">None recorded.</p> : (
            <ul className="small" style={{ paddingLeft: 18, margin: 0 }}>
              {a.warnings.map(w => <li key={w} style={{ color: 'var(--signal)' }}>{w}</li>)}
              {ex.unknowns.map(u => <li key={u}>{u}</li>)}
            </ul>
          )}
        </section>
      </div>

      <div className="spacer" />
      <section className="card">
        <div className="card-head"><h2>Findings</h2><span className="label">Gaps first · covered last</span></div>
        {findingGroups(a, recs).map(g => g.findings.length === 0 ? null : g.collapsed
          ? (
            <details key={g.key} className={`finding-group ${g.key}`}>
              <summary><span className="label">{g.title} ({g.findings.length})</span><span className="small muted">{g.hint}</span></summary>
              {g.findings.map(f => <FindingRow key={f.requirementId} f={f} a={a} onCorrected={onCorrected} readOnly={readOnly} />)}
            </details>
          )
          : (
            <div key={g.key} className={`finding-group ${g.key}`}>
              <div className="group-head"><span className="label">{g.title} ({g.findings.length})</span><span className="small muted">{g.hint}</span></div>
              {g.findings.map(f => <FindingRow key={f.requirementId} f={f} a={a} onCorrected={onCorrected} readOnly={readOnly}
                partnerNames={g.key === 'gaps' ? partnersFor(f, recs) : undefined} />)}
            </div>
          ))}
      </section>
    </>
  )
}

type FindingGroup = { key: string; title: string; hint: string; collapsed: boolean; findings: Finding[] }

/** Groups findings by what the company has to do: gaps, own homework, open questions, then what is already covered. */
function findingGroups(a: Assessment, recs?: Recommendations | null): FindingGroup[] {
  const byId = (x: Finding, y: Finding) => x.requirementId.localeCompare(y.requirementId)
  const fs = [...a.findings].sort(byId)
  const capacityGapsPartnerable = recs ? recs.feasible : a.extraction.partnershipPermission !== 'PROHIBITED'
  return [
    {
      key: 'gaps', title: 'Gaps', collapsed: false,
      hint: capacityGapsPartnerable
        ? 'Not met with your declared data. A partner can only cover location hours, never credentials.'
        : 'Not met with your declared data. The document prohibits partnering, so you must cover these yourself.',
      findings: fs.filter(f => f.status === 'GAP'),
    },
    {
      key: 'homework', title: 'Your homework', collapsed: false,
      hint: 'Evidence or profile information only you can provide. A partner cannot fix these.',
      findings: fs.filter(f => f.status === 'MISSING_EVIDENCE' || (f.status === 'UNKNOWN' && (f.kind === 'QUALIFICATION' || f.kind === 'OTHER'))),
    },
    {
      key: 'confirm', title: 'To confirm', collapsed: false,
      hint: 'The document or the buyer does not settle these yet.',
      findings: fs.filter(f => f.status === 'UNKNOWN' && (f.kind === 'CAPACITY' || f.kind === 'PARTNERING')),
    },
    {
      key: 'conditional', title: 'Covered with conditions', collapsed: false,
      hint: 'Met only thanks to a partner, subject to the partnering conditions.',
      findings: fs.filter(f => f.status === 'MATCH' && f.conditional),
    },
    {
      key: 'covered', title: 'Already covered', collapsed: true,
      hint: 'Met by your confirmed profile. Click to expand.',
      findings: fs.filter(f => f.status === 'MATCH' && !f.conditional),
    },
  ]
}

function partnersFor(f: Finding, recs?: Recommendations | null): string[] | undefined {
  if (!recs || f.kind !== 'CAPACITY' || !recs.gaps.some(g => g.requirementId === f.requirementId)) return undefined
  return recs.feasible
    ? recs.recommendations.filter(r => r.candidate.addresses.some(g => g.requirementId === f.requirementId)).map(r => r.candidate.companyName)
    : []
}

function FindingRow({ f, a, onCorrected, readOnly, partnerNames }: { f: Finding; a: Assessment; onCorrected?: (a: Assessment) => void; readOnly?: boolean; partnerNames?: string[] }) {
  const [editing, setEditing] = useState(false)
  const req = a.extraction.requirements.find(r => r.id === f.requirementId)
  return (
    <div className="finding">
      <div>
        <div className="req-id">{f.requirementId}</div>
        <div style={{ marginTop: 8 }}><StatusMark status={f.status} conditional={f.conditional} /></div>
      </div>
      <div>
        <div className="tags">
          <span className="tag">{CATEGORY_LABEL[f.category]}</span>
          <span className="tag">{APPLIES_LABEL[f.appliesTo]}</span>
          {f.humanReviewed && <span className="tag human">✓ Human-reviewed</span>}
          {f.conditional && <span className="tag cond">Conditional</span>}
        </div>
        <h3>{f.description}</h3>
        {f.sourceExcerpt
          ? <blockquote className={`excerpt${f.excerptVerified ? '' : ' unverified'}`}>“{f.sourceExcerpt}”<cite>{f.sourceReference ?? 'reference unknown'}{f.excerptVerified ? ' · verified in document' : ' · NOT found in document: unsupported'}</cite></blockquote>
          : <Unknown>No source excerpt</Unknown>}
        {req?.validationNotes?.filter(n => !n.startsWith('Human')).map(n => <div key={n} className="tiny" style={{ color: 'var(--signal)', marginTop: 4 }}>{n}</div>)}
      </div>
      <div>
        <div className="label">Company evidence</div>
        <div className="small" style={{ marginTop: 3 }}>{f.companyEvidence}</div>
        {f.members.length > 1 && (
          <div className="members">{f.members.map(m => (
            <div key={m.companyId + m.role} className="member"><StatusMark status={m.status} /><span><b>{m.companyName}</b> ({m.role}): {m.note}</span></div>
          ))}</div>
        )}
        <p className="small muted" style={{ margin: '8px 0 0' }}>{f.explanation}</p>
        {f.action && f.action !== 'None' && <div className="action-line">{f.action}</div>}
        {partnerNames && (
          <div className="partner-hint">
            {partnerNames.length > 0
              ? <>⚑ Partner found: <b>{partnerNames.join(', ')}</b>{' '}
                  <button className="link-btn" onClick={() => document.getElementById('partners')?.scrollIntoView()}>See partner and invite ↓</button></>
              : <span className="muted">No opted-in partner currently declares capacity here.</span>}
          </div>
        )}
        {!readOnly && onCorrected && req && (editing
          ? <CorrectionForm a={a} reqId={f.requirementId} onDone={next => { setEditing(false); if (next) onCorrected(next) }} />
          : <button className="link-btn" style={{ marginTop: 10 }} onClick={() => setEditing(true)}>Correct this requirement</button>)}
      </div>
    </div>
  )
}

function CorrectionForm({ a, reqId, onDone }: { a: Assessment; reqId: string; onDone: (a: Assessment | null) => void }) {
  const r = a.extraction.requirements.find(x => x.id === reqId)!
  const [form, setForm] = useState({
    description: r.description, category: r.category, kind: r.kind, appliesTo: r.appliesTo,
    area: r.area ?? '', weeklyHours: r.weeklyHours ?? 0, qualificationName: r.qualificationName ?? '', reviewerNote: '',
  })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<unknown>(null)
  async function submit() {
    setBusy(true)
    try {
      const next = await api<Assessment>(`/assessments/${a.id}/requirements/${reqId}`, {
        method: 'PATCH',
        json: { ...form, area: form.area || null, qualificationName: form.qualificationName || null, weeklyHours: form.kind === 'CAPACITY' ? form.weeklyHours : null },
      })
      onDone(next)
    } catch (e) {
      setErr(e)
      setBusy(false)
    }
  }
  return (
    <div className="card sunk" style={{ marginTop: 12, padding: 14 }}>
      <div className="label" style={{ marginBottom: 8 }}>Human correction · kept for this document version</div>
      <div className="stack">
        <div className="field"><label>Description</label><input type="text" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
        <div className="grid two" style={{ gap: 12 }}>
          <div className="field"><label>Category</label>
            <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value as Category })}>
              {Object.entries(CATEGORY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          <div className="field"><label>Kind</label>
            <select value={form.kind} onChange={e => setForm({ ...form, kind: e.target.value as Kind })}>
              {['CAPACITY', 'QUALIFICATION', 'PARTNERING', 'OTHER'].map(k => <option key={k}>{k}</option>)}</select></div>
          <div className="field"><label>Applies to</label>
            <select value={form.appliesTo} onChange={e => setForm({ ...form, appliesTo: e.target.value as AppliesTo })}>
              {Object.entries(APPLIES_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          {form.kind === 'CAPACITY' && <>
            <div className="field"><label>Location</label><input type="text" value={form.area} onChange={e => setForm({ ...form, area: e.target.value })} /></div>
            <div className="field"><label>Weekly hours</label><input type="number" min={0} value={form.weeklyHours} onChange={e => setForm({ ...form, weeklyHours: Number(e.target.value) || 0 })} /></div>
          </>}
          {form.kind === 'QUALIFICATION' && <div className="field"><label>Qualification</label><input type="text" value={form.qualificationName} onChange={e => setForm({ ...form, qualificationName: e.target.value })} /></div>}
        </div>
        <div className="field"><label>Reviewer note</label><input type="text" value={form.reviewerNote} onChange={e => setForm({ ...form, reviewerNote: e.target.value })} placeholder="why this was corrected" /></div>
        {err != null && <ErrorNotice error={err} />}
        <div className="row"><button className="btn small" onClick={submit} disabled={busy}>{busy ? 'Saving…' : 'Save & reassess'}</button><button className="btn small ghost" onClick={() => onDone(null)}>Cancel</button></div>
      </div>
    </div>
  )
}

// ---------------- partner recommendations (F4) ----------------

function PartnerPanel({ a, onInvited, flagOf }: { a: Assessment; onInvited: () => void; flagOf: (id?: string) => ReturnType<ReturnType<typeof useApp>['flag']> }) {
  const { status } = useApp()
  const [ai, setAi] = useState(false)
  const recs = useLoad(() => api<Recommendations>(`/tenders/${a.tenderId}/partner-recommendations?aiExplanation=${ai}`), [a.tenderId, a.id, ai])
  const [inviting, setInviting] = useState<Recommendation | null>(null)

  return (
    <section className="card" id="partners">
      <div className="card-head">
        <div>
          <div className="eyebrow">§03b · Partner recommendations</div>
          <h2 style={{ marginTop: 6 }}>Who could cover the gap?</h2>
        </div>
        {status?.liveAiAvailable && (
          <label className="check small"><input type="checkbox" checked={ai} onChange={e => setAi(e.target.checked)} /><span>AI-written fit explanation</span></label>
        )}
      </div>
      {recs.loading && <Loading label="Scanning opted-in profiles…" />}
      {recs.error != null && <ErrorNotice error={recs.error} onRetry={recs.reload} />}
      {recs.data && (() => {
        const d = recs.data
        return (
          <>
            <div className="notice info small" style={{ marginBottom: 16 }}><span className="glyph">i</span><div>{d.disclaimer} Only opted-in, confirmed profiles stored in TenderReady are considered{d.excludedOptedOutOrUnconfirmed > 0 ? ` (${d.excludedOptedOutOrUnconfirmed} other profile(s) not shown: opted out or unconfirmed)` : ''}.</div></div>
            {d.recalculatedFromCurrentProfile && <div className="notice warn small" style={{ marginBottom: 16 }}><span className="glyph">⟳</span><div>Recalculated from your current profile, which changed since the assessment.</div></div>}
            {d.gaps.length === 0
              ? <div className="empty small"><h3>No location capacity gaps</h3>Your declared capacity covers every stated location requirement. Partnering is not needed for capacity.</div>
              : !d.feasible
                ? <div className="empty"><h3>Partnering is not a feasible path</h3><p className="small">The document prohibits the arrangement. {d.partnershipExcerpt && <>“{d.partnershipExcerpt}”</>}</p><Alternatives list={d.alternatives} /></div>
                : d.recommendations.length === 0
                  ? <div className="empty"><h3>No suitable partner found</h3><p className="small">No opted-in, confirmed profile declares capacity for {d.gaps.map(g => `${g.shortfall} h/week in ${g.area}`).join(' and ')}.</p><Alternatives list={d.alternatives} /></div>
                  : (
                    <div className="stack" style={{ gap: 16 }}>
                      {d.recommendations.map((r, i) => (
                        <article key={r.candidate.companyId} className="card flat partner-card">
                          <div className="partner-head">
                            <div className="row" style={{ gap: 14 }}>
                              <SignalFlag kind={flagOf(r.candidate.companyId)} size={52} />
                              <div>
                                <div className="mono tiny muted">Recommendation #{i + 1} · not a win probability</div>
                                <h2>{r.candidate.companyName}</h2>
                                <div className="small muted">{r.candidate.services.join(', ')} · {r.candidate.serviceAreas.join(', ')}</div>
                              </div>
                            </div>
                            <div className="stack" style={{ justifyItems: 'end' }}>
                              <span className="stamp fixture">{r.candidate.label}</span>
                              {r.existingInvitation
                                ? <a className="btn ghost small" href={`#/collaboration/${r.existingInvitation.id}`}>Invitation {r.existingInvitation.state.replace(/_/g, ' ').toLowerCase()} →</a>
                                : <button className="btn signal" onClick={() => setInviting(r)}>Approve and invite…</button>}
                            </div>
                          </div>
                          <div className="grid two" style={{ marginTop: 16 }}>
                            <div>
                              <div className="label">Gaps this candidate could address</div>
                              {r.candidate.addresses.map(g => (
                                <div key={g.requirementId} style={{ marginTop: 8 }}>
                                  <div className="small"><b>{g.requirementId} · {g.area}</b>: declares {g.declaredHours} h/week vs {g.shortfall} h/week shortfall {g.fullyCovers ? '' : '(partial)'}</div>
                                  <div className="meter" style={{ marginTop: 6 }}><i style={{ width: `${Math.min(100, (g.suggestedHours / g.shortfall) * 100)}%` }} /></div>
                                </div>
                              ))}
                              <div className="label" style={{ marginTop: 14 }}>Per-company requirements</div>
                              {r.candidate.credentials.map(c => <div key={c.requirementId} className="member" style={{ marginTop: 6 }}><StatusMark status={c.status} /><span className="small">{c.requirement}: {c.note}</span></div>)}
                            </div>
                            <div>
                              <div className="label">Why this fit</div>
                              <p className="small" style={{ marginTop: 6 }}>{r.explanation}</p>
                              <div className="tiny muted mono">{r.explanationSource}</div>
                              {r.partnershipPreference && <p className="small"><b>Their preference:</b> {r.partnershipPreference}</p>}
                            </div>
                          </div>
                        </article>
                      ))}
                    </div>
                  )}
            {d.unresolvedForLead.length > 0 && (
              <div style={{ marginTop: 18 }}>
                <div className="label">Still unresolved for {a.company.name}, whatever partner is chosen</div>
                {d.unresolvedForLead.map(u => <div key={u.requirementId} className="member" style={{ marginTop: 6 }}><StatusMark status={u.status} /><span className="small"><b>{u.requirementId}</b> {u.description}</span></div>)}
              </div>
            )}
          </>
        )
      })()}
      {inviting && <InviteDialog a={a} rec={inviting} onClose={sent => { setInviting(null); if (sent) { recs.reload(); onInvited() } }} />}
    </section>
  )
}

function Alternatives({ list }: { list: string[] }) {
  return <ul className="small" style={{ textAlign: 'left', maxWidth: 560, margin: '10px auto 0' }}>{list.map(x => <li key={x}>{x}</li>)}</ul>
}

function InviteDialog({ a, rec, onClose }: { a: Assessment; rec: Recommendation; onClose: (sent: boolean) => void }) {
  const { toast, refreshInvitations } = useApp()
  const [c, setC] = useState<ProposedContribution>(rec.suggestedContribution)
  const [message, setMessage] = useState(`We are preparing a bid for "${a.tenderTitle}" and would like to explore you covering our ${c.area} delivery gap.`)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<unknown>(null)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  // Modal behaviour: focus the title, close on Escape, return focus to the trigger on close.
  useEffect(() => {
    const trigger = document.activeElement as HTMLElement | null
    titleRef.current?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') closeRef.current(false) }
    document.addEventListener('keydown', onKey)
    const main = document.querySelector('.shell') as HTMLElement | null
    main?.setAttribute('inert', '')
    return () => {
      document.removeEventListener('keydown', onKey)
      main?.removeAttribute('inert')
      trigger?.focus?.()
    }
  }, [])

  async function send() {
    setBusy(true)
    setErr(null)
    try {
      const inv = await api<{ id: string }>('/invitations', {
        method: 'POST', json: { tenderId: a.tenderId, recipientCompanyId: rec.candidate.companyId, proposedContribution: c, message },
      })
      toast(`Invitation sent to ${rec.candidate.companyName} (in-app only).`)
      await refreshInvitations()
      onClose(true)
      go(`collaboration/${inv.id}`)
    } catch (e) {
      setErr(e instanceof ApiError ? e : new Error(String(e)))
      setBusy(false)
    }
  }

  return createPortal(
    <div className="overlay" role="dialog" aria-modal="true" aria-labelledby="invite-title" onClick={e => { if (e.target === e.currentTarget) onClose(false) }}>
      <div className="card dialog">
        <div className="eyebrow">Human approval required</div>
        <h2 id="invite-title" ref={titleRef} tabIndex={-1} style={{ margin: '8px 0 4px' }}>Approve and invite {rec.candidate.companyName}</h2>
        <p className="small muted" style={{ marginTop: 0 }}>Nothing is sent outside TenderReady. The recipient sees an in-app invitation; acceptance means interest to explore, not a contract.</p>
        <dl className="kv" style={{ margin: '14px 0' }}>
          <dt>Tender</dt><dd>{a.tenderTitle} {a.tenderDemo && <span className="stamp neutral">Sample scenario</span>}</dd>
          <dt>Addresses</dt><dd className="mono small">{c.addressesRequirementIds.join(', ')}</dd>
        </dl>
        <div className="grid two" style={{ gap: 14 }}>
          <div className="field"><label>Location</label><input type="text" value={c.area} onChange={e => setC({ ...c, area: e.target.value })} /></div>
          <div className="field"><label>Weekly hours requested</label><input type="number" min={1} value={c.weeklyHours} onChange={e => setC({ ...c, weeklyHours: Number(e.target.value) || 0 })} /></div>
          <div className="field"><label>Service</label><input type="text" value={c.service} onChange={e => setC({ ...c, service: e.target.value })} /></div>
          <div className="field"><label>Their proposed role</label><input type="text" value={c.partnerRole} onChange={e => setC({ ...c, partnerRole: e.target.value })} /></div>
          <div className="field"><label>Your role</label><input type="text" value={c.leadRole} onChange={e => setC({ ...c, leadRole: e.target.value })} /></div>
        </div>
        <div className="field" style={{ marginTop: 14 }}><label>Responsibilities</label><textarea value={c.responsibilities ?? ''} onChange={e => setC({ ...c, responsibilities: e.target.value })} /></div>
        <div className="field" style={{ marginTop: 14 }}><label>Message</label><textarea value={message} onChange={e => setMessage(e.target.value)} /></div>
        {err != null && <div style={{ marginTop: 12 }}><ErrorNotice error={err} /></div>}
        <div className="row" style={{ marginTop: 18, justifyContent: 'flex-end' }}>
          <button className="btn ghost" onClick={() => onClose(false)}>Cancel</button>
          <button className="btn signal" onClick={send} disabled={busy || !c.area || c.weeklyHours < 1}>{busy ? 'Sending…' : 'Approve and invite'}</button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
