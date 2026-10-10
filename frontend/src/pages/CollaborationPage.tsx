import { useState } from 'react'
import { api } from '../api'
import { go, useApp, useLoad } from '../context'
import { SignalFlag } from '../components/Flag'
import { ErrorNotice, Loading, ModeStamp, StatusMark, TenderStamp } from '../components/Marks'
import { NovaScotiaChart } from '../components/NovaScotiaChart'
import { STATE_LABEL, fmtDate } from '../components/format'
import type { Assessment, Invitation } from '../types'
import { AssessmentBody, chartSites } from './AssessmentPage'
import { ReadinessGauges } from '../components/ReadinessGauges'

export function CollaborationPage() {
  const { activeId, version } = useApp()
  const list = useLoad(() => api<Invitation[]>('/invitations'), [activeId, version])
  return (
    <>
      <header className="page-head">
        <div>
          <div className="eyebrow">§04 · Collaboration</div>
          <h1>Signals <em>exchanged</em>.</h1>
          <p className="lede">In-app invitations between companies. Nothing here sends email, signs agreements or submits a bid. Every step needs a person from the right company.</p>
        </div>
      </header>
      {list.loading && <Loading />}
      {list.error != null && <ErrorNotice error={list.error} onRetry={list.reload} />}
      {list.data && list.data.length === 0 && (
        <div className="empty"><h3>No invitations yet</h3><p className="small">Invitations appear here after a company explicitly approves one from a partner recommendation.</p></div>
      )}
      {list.data && list.data.length > 0 && <InvitationTable rows={list.data} />}
    </>
  )
}

function InvitationTable({ rows }: { rows: Invitation[] }) {
  const { flag } = useApp()
  return (
    <div className="card flat" style={{ padding: 0, overflowX: 'auto' }}>
      <table className="ledger responsive">
        <thead><tr><th>Direction</th><th>Tender</th><th>Contribution</th><th>State</th><th>Your move</th></tr></thead>
        <tbody>
          {rows.map(i => {
            const other = i.viewerRole === 'SENDER' ? i.recipient : i.sender
            const mine = i.availableActions.filter(a => a !== 'WITHDRAW')
            return (
              <tr key={i.id} className="clickable" onClick={() => go(`collaboration/${i.id}`)}>
                <td><div className="row"><SignalFlag kind={flag(other.id)} size={30} /><span className="small">{i.viewerRole === 'SENDER' ? 'Sent to' : 'From'} <b>{other.name}</b></span></div></td>
                <td className="small" data-label="Tender"><a href={`#/collaboration/${i.id}`} style={{ textDecoration: 'none', fontWeight: 600 }}>{i.tenderTitle}</a> {i.tenderDemo && <span className="stamp neutral">Sample scenario</span>}</td>
                <td className="small mono" data-label="Contribution">{i.proposedContribution.weeklyHours} h/wk · {i.proposedContribution.area}</td>
                <td data-label="State"><span className="tag">{STATE_LABEL[i.state]}</span></td>
                <td className="small" data-label="Your move">{mine.length ? <b style={{ color: 'var(--signal)' }}>{mine.map(a => a.replace(/_/g, ' ').toLowerCase()).join(' / ')}</b> : <span className="muted">waiting</span>}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export function InvitationDetailPage({ id }: { id: string }) {
  const { activeId, version, flag, refreshInvitations, toast } = useApp()
  const inv = useLoad(() => api<Invitation>(`/invitations/${id}`), [id, activeId, version])
  const combined = useLoad(
    () => (inv.data?.latestCombinedAssessmentId ? api<Assessment>(`/assessments/${inv.data.latestCombinedAssessmentId}`) : Promise.resolve(null)),
    [inv.data?.latestCombinedAssessmentId, version],
  )
  const [busy, setBusy] = useState<string | null>(null)
  const [err, setErr] = useState<unknown>(null)

  if (inv.error) return (
    <>
      <a className="link-btn" href="#/collaboration">← Invitations</a>
      <div className="spacer" />
      <ErrorNotice error={inv.error} />
      <p className="small muted">Invitations are only visible to the two companies involved. Hoist the other company's flag if this is theirs.</p>
    </>
  )
  if (inv.loading || !inv.data) return <Loading />
  const i = inv.data

  async function act(action: string) {
    setBusy(action)
    setErr(null)
    try {
      if (action === 'REASSESS') {
        const a = await api<Assessment>(`/invitations/${id}/reassess`, { method: 'POST' })
        combined.setData(a)
        inv.reload()
        toast('Combined assessment ready.')
      } else {
        const path = action === 'ACCEPT_INTEREST' || action === 'DECLINE' ? 'respond'
          : action === 'CONFIRM_CONTRIBUTION' ? 'confirm-contribution' : 'withdraw'
        const next = await api<Invitation>(`/invitations/${id}/${path}`, {
          method: 'POST', json: path === 'respond' ? { response: action } : undefined,
        })
        inv.setData(next)
        toast(`Invitation ${STATE_LABEL[next.state].toLowerCase()}.`)
      }
      await refreshInvitations()
    } catch (e) {
      setErr(e)
    } finally {
      setBusy(null)
    }
  }

  const steps = [
    { key: 'INVITED', title: 'Invited', sub: `by ${i.sender.name}`, done: true },
    { key: 'INTEREST', title: i.state === 'DECLINED' ? 'Declined' : 'Interest accepted', sub: i.respondedAt ? fmtDate(i.respondedAt, false) : 'recipient decides', done: !!i.respondedAt, failed: i.state === 'DECLINED' },
    { key: 'CONFIRM', title: 'Both confirm', sub: `${[i.senderConfirmedAt, i.recipientConfirmedAt].filter(Boolean).length} of 2 seals`, done: i.state === 'CONFIRMED_FOR_ASSESSMENT' },
    { key: 'ASSESS', title: 'Reassessed', sub: combined.data ? fmtDate(combined.data.createdAt, false) : 'combined plan', done: !!combined.data },
  ]
  const currentIdx = steps.findIndex(s => !s.done)
  const isRecipient = i.viewerRole === 'RECIPIENT'
  const c = i.proposedContribution

  return (
    <>
      <header className="page-head">
        <div>
          <a className="link-btn" href="#/collaboration">← Invitations</a>
          <div className="eyebrow" style={{ marginTop: 14 }}>§04 · Invitation · you are the {isRecipient ? 'recipient' : 'sender'}</div>
          <h1 className="row" style={{ gap: 16 }}>
            <SignalFlag kind={flag(i.sender.id)} size={52} />
            <span style={{ fontSize: '0.7em' }}>→</span>
            <SignalFlag kind={flag(i.recipient.id)} size={52} />
            <span>{i.sender.name.replace(' Demo', '')} &amp; {i.recipient.name.replace(' Demo', '')}</span>
          </h1>
          <p className="lede">{i.tenderTitle}</p>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          <TenderStamp demo={i.tenderDemo} />
          <span className={`stamp ${i.state === 'CONFIRMED_FOR_ASSESSMENT' ? 'live' : i.state === 'DECLINED' || i.state === 'WITHDRAWN' ? 'ai' : 'fixture'} big`}>{STATE_LABEL[i.state]}</span>
        </div>
      </header>

      <section className="card">
        <div className="course">
          {steps.map((s, idx) => (
            <div key={s.key} className={`waypoint ${s.failed ? 'failed' : s.done ? 'done' : idx === currentIdx && i.state !== 'WITHDRAWN' ? 'current' : ''}`}>
              <div className="dot">{s.failed ? '×' : s.done ? '✓' : idx + 1}</div>
              <div className="wp-title">{s.title}</div>
              <div className="wp-sub">{s.sub}</div>
            </div>
          ))}
        </div>
      </section>

      {err != null && <div style={{ marginTop: 18 }}><ErrorNotice error={err} /></div>}
      <div className="spacer" />

      <div className="grid sidebar">
        <section className="card">
          <div className="card-head"><h2>Proposed contribution</h2><span className="label">Proposal · not binding</span></div>
          <dl className="kv">
            <dt>Location</dt><dd><b>{c.area}</b></dd>
            <dt>Service</dt><dd>{c.service}</dd>
            <dt>Weekly hours</dt><dd><span className="counter" style={{ fontSize: 30 }}>{c.weeklyHours}</span> <span className="small muted">h/week requested from {i.recipient.name}</span></dd>
            <dt>{i.recipient.name.replace(' Demo', '')} role</dt><dd>{c.partnerRole}</dd>
            <dt>{i.sender.name.replace(' Demo', '')} role</dt><dd>{c.leadRole}</dd>
            <dt>Responsibilities</dt><dd className="small">{c.responsibilities ?? '—'}</dd>
            <dt>Addresses</dt><dd className="mono small">{c.addressesRequirementIds?.join(', ') || '—'}</dd>
            {i.message && <><dt>Message</dt><dd className="small" style={{ fontStyle: 'italic' }}>“{i.message}”</dd></>}
          </dl>
        </section>

        <aside className="stack" style={{ gap: 18 }}>
          <section className="card">
            <div className="card-head"><h3>Your move</h3></div>
            {i.state === 'INVITED' && isRecipient && <p className="small" style={{ marginTop: 0 }}>Accepting means you are willing to <b>explore</b> this tender together. It is not a contract. You will confirm the contribution separately.</p>}
            {i.state === 'INVITED' && !isRecipient && <p className="small" style={{ marginTop: 0 }}>Waiting for {i.recipient.name} to respond. Hoist their flag to respond as them.</p>}
            {i.state === 'INTEREST_ACCEPTED' && <p className="small" style={{ marginTop: 0 }}>Each company confirms the proposed contribution, capacity and responsibilities. One-sided confirmation cannot confirm the collaboration.</p>}
            {i.state === 'CONFIRMED_FOR_ASSESSMENT' && <p className="small" style={{ marginTop: 0 }}>Both companies confirmed. Reassess to see which gaps the team addresses, and what stays open.</p>}
            {(i.state === 'DECLINED' || i.state === 'WITHDRAWN') && <p className="small" style={{ marginTop: 0 }}>This invitation is closed.</p>}
            <div className="row">
              {i.availableActions.includes('ACCEPT_INTEREST') && <button className="btn kelp" disabled={!!busy} onClick={() => act('ACCEPT_INTEREST')}>Accept interest</button>}
              {i.availableActions.includes('DECLINE') && <button className="btn ghost" disabled={!!busy} onClick={() => act('DECLINE')}>Decline</button>}
              {i.availableActions.includes('CONFIRM_CONTRIBUTION') && <button className="btn kelp" disabled={!!busy} onClick={() => act('CONFIRM_CONTRIBUTION')}>✓ Confirm contribution</button>}
              {i.availableActions.includes('REASSESS') && <button className="btn signal" disabled={!!busy} onClick={() => act('REASSESS')}>{busy === 'REASSESS' ? 'Reassessing…' : 'Reassess tender'}</button>}
              {i.availableActions.includes('WITHDRAW') && <button className="btn ghost small" disabled={!!busy} onClick={() => act('WITHDRAW')}>Withdraw</button>}
            </div>
          </section>
          <Seal name={i.sender.name} flagKind={flag(i.sender.id)} role="Sender / lead" at={i.senderConfirmedAt} />
          <Seal name={i.recipient.name} flagKind={flag(i.recipient.id)} role="Recipient / partner" at={i.recipientConfirmedAt} />
        </aside>
      </div>

      {combined.data && <CombinedPlan a={combined.data} />}
      {combined.error != null && <div style={{ marginTop: 18 }}><ErrorNotice error={combined.error} /></div>}
    </>
  )
}

function Seal({ name, flagKind, role, at }: { name: string; flagKind: ReturnType<ReturnType<typeof useApp>['flag']>; role: string; at: string | null }) {
  return (
    <div className={`seal${at ? ' sealed' : ''}`}>
      <SignalFlag kind={flagKind} size={52} />
      <div>
        <div className="label">{role}</div>
        <b>{name}</b>
        <div className="small">{at ? <>Confirmed contribution · {fmtDate(at)}</> : <span className="muted">Not yet confirmed</span>}</div>
      </div>
    </div>
  )
}

function CombinedPlan({ a }: { a: Assessment }) {
  const { flag } = useApp()
  const baseline = useLoad(() => (a.baselineAssessmentId ? api<Assessment>(`/assessments/${a.baselineAssessmentId}`) : Promise.resolve(null)), [a.baselineAssessmentId])
  return (
    <section style={{ marginTop: 34 }}>
      <div className="page-head" style={{ marginBottom: 20 }}>
        <div>
          <div className="eyebrow">§04b · Updated readiness plan</div>
          <h1 style={{ fontSize: 40 }}>Before &amp; <em>after</em> partnering.</h1>
          <p className="lede">Capacity is matched per location. Nothing is added across cities, and credentials are not pooled. Buyer approval and each company's evidence stay open until evidenced.</p>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          <ModeStamp mode={a.mode} />
          {a.stale && <span className="stamp ai">Stale · reassess</span>}
        </div>
      </div>
      {a.stale && <div className="notice stale" style={{ marginBottom: 18 }}><span className="glyph">⟳</span><div><b>Stale.</b> {a.staleReasons.join(' ')}</div></div>}
      <ReadinessGauges a={a} before={baseline.data} />
      <div className="spacer" />

      <div className="grid two">
        <div>
          <div className="label" style={{ marginBottom: 8 }}>Before: {a.company.name} alone</div>
          {baseline.data ? <NovaScotiaChart sites={chartSites(baseline.data, flag)} caption="Before partnering" /> : <Loading />}
        </div>
        <div>
          <div className="label" style={{ marginBottom: 8 }}>After: with {a.partners.map(p => p.name).join(', ')} (confirmed for assessment)</div>
          <NovaScotiaChart sites={chartSites(a, flag)} caption="After partnering" />
        </div>
      </div>

      <div className="spacer" />
      <section className="card">
        <div className="card-head"><h2>What changed</h2><span className="label">Requirement · before → after</span></div>
        {a.comparison?.map(c => (
          <div key={c.requirementId} className={`compare-row${c.changed ? ' changed' : ''}`}>
            <span className="mono small">{c.requirementId}</span>
            <span className="small">{c.description}</span>
            <span>{c.before ? <StatusMark status={c.before} conditional={c.beforeConditional} /> : '—'}</span>
            <span className="arrow">{c.changed ? '⟶' : '='}</span>
            <span><StatusMark status={c.after} conditional={c.afterConditional} /></span>
          </div>
        ))}
      </section>

      <div className="spacer" />
      <AssessmentBody a={a} readOnly hideChart hideGauges />
    </section>
  )
}
