import { useState } from 'react'
import { api } from '../api'
import { go, useApp, useLoad } from '../context'
import { ErrorNotice, Loading, Stamp, TenderStamp, Unknown } from '../components/Marks'
import { daysUntil, fmtDate } from '../components/format'
import type { ImportRun, Mode, TenderDetail, TenderList, TenderSummary } from '../types'

export function TendersPage() {
  const [service, setService] = useState('')
  const [location, setLocation] = useState('')
  const [includeUnknown, setIncludeUnknown] = useState(true)
  const [query, setQuery] = useState({ service: '', location: '', includeUnknown: true })
  const [importing, setImporting] = useState(false)
  const [lastRun, setLastRun] = useState<ImportRun | null>(null)
  const params = new URLSearchParams({ service: query.service, location: query.location, includeUnknownLocation: String(query.includeUnknown) })
  const { data, error, loading, reload } = useLoad(() => api<TenderList>(`/tenders?${params}`), [query.service, query.location, query.includeUnknown])

  async function refresh() {
    setImporting(true)
    try {
      setLastRun(await api<ImportRun>('/tenders/import', { method: 'POST' }))
      reload()
    } catch (e) {
      setLastRun({ status: 'FAILED', error: e instanceof Error ? e.message : String(e) } as ImportRun)
    } finally {
      setImporting(false)
    }
  }

  const run = lastRun ?? data?.lastImport ?? null

  return (
    <>
      <header className="page-head">
        <div>
          <div className="eyebrow">§02 · Tenders</div>
          <h1>Public opportunities, <em>sounded</em> for fit.</h1>
          <p className="lede">Official notices come from the national <b>CanadaBuys: federal opportunities</b> feed. It is not a complete Nova Scotia provincial feed. A sample scenario is kept separate from the live feed.</p>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          <button className="btn" onClick={refresh} disabled={importing}>{importing ? 'Importing…' : '↻ Refresh public tenders'}</button>
          <span className="mono tiny muted">Manual import · daily schedule optional</span>
        </div>
      </header>

      {importing && <Loading label="Downloading the CanadaBuys open notice CSV…" />}
      {run && !importing && <ImportResult run={run} fresh={!!lastRun} />}

      <section className="card" style={{ margin: '22px 0' }}>
        <form className="row" style={{ alignItems: 'flex-end', gap: 18 }} onSubmit={e => { e.preventDefault(); setQuery({ service, location, includeUnknown }) }}>
          <div className="field" style={{ flex: '2 1 220px' }}><label htmlFor="svc">Service or keyword</label>
            <input id="svc" type="search" value={service} onChange={e => setService(e.target.value)} placeholder="e.g. cleaning, janitorial" /></div>
          <div className="field" style={{ flex: '1 1 180px' }}><label htmlFor="loc">Delivery location</label>
            <input id="loc" type="search" value={location} onChange={e => setLocation(e.target.value)} placeholder="e.g. Nova Scotia" /></div>
          <label className="check" style={{ flex: '1 1 220px' }}>
            <input type="checkbox" checked={includeUnknown} onChange={e => setIncludeUnknown(e.target.checked)} />
            <span className="small">Include notices with <b>unknown</b> delivery location</span>
          </label>
          <button className="btn ghost" type="submit">Filter</button>
        </form>
      </section>

      {error != null && <ErrorNotice error={error} onRetry={reload} />}
      {loading && !data && <Loading />}
      {data && (
        <>
          <div className="label" style={{ margin: '6px 0 10px' }}>Sample scenario</div>
          {data.demo.length === 0 && <div className="empty small">The sample tender does not match this filter.</div>}
          {data.demo.map(t => <DemoTenderCard key={t.id} t={t} />)}

          <div className="row between" style={{ margin: '30px 0 10px' }}>
            <div className="label">{data.liveSourceLabel} · showing {data.live.length} of {data.liveMatching} matching ({data.liveTotal} imported)</div>
            {data.lastSuccessfulImport && <span className="mono tiny muted">Last successful import {fmtDate(data.lastSuccessfulImport.finishedAt)}</span>}
          </div>
          {data.liveTotal === 0
            ? <div className="empty"><h3>No official notices imported yet</h3><p className="small">Use “Refresh public tenders” to import the CanadaBuys feed. The fictional tender above remains available either way.</p></div>
            : data.live.length === 0
              ? <div className="empty"><h3>No notices match</h3><p className="small">Try a broader keyword or include unknown delivery locations.</p></div>
              : <LiveTable rows={data.live} />}
        </>
      )}
    </>
  )
}

function ImportResult({ run, fresh }: { run: ImportRun; fresh: boolean }) {
  if (run.status === 'FAILED') {
    return (
      <div className="notice error" role="alert">
        <span className="glyph">⚠</span>
        <div><b>Import failed.</b> {run.error} Previously imported notices are still shown.</div>
      </div>
    )
  }
  return (
    <div className={`notice ${fresh ? 'ok' : 'info'}`}>
      <span className="glyph">⚓</span>
      <div>
        <b>{fresh ? 'Import succeeded.' : 'Last import:'}</b>{' '}
        {run.rowsRead} rows read · <b>{run.inserted}</b> new · <b>{run.updated}</b> updated · {run.unchanged} unchanged · {run.skipped} skipped
        <span className="muted"> · {fmtDate(run.finishedAt)}</span>
        <div className="tiny muted">Deduplicated by CanadaBuys reference number. Notices missing from a later import are kept, not cancelled.</div>
      </div>
    </div>
  )
}

function DemoTenderCard({ t }: { t: TenderSummary }) {
  const days = daysUntil(t.closingAt)
  return (
    <article className="card" style={{ cursor: 'pointer' }} onClick={() => go(`tenders/${t.id}`)}>
      <div className="row between" style={{ alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 260 }}>
          <div className="row" style={{ marginBottom: 8 }}><TenderStamp demo /> <span className="mono tiny muted">{t.sourceId}</span></div>
          <h2>{t.title}</h2>
          <p className="small muted" style={{ margin: '6px 0 0' }}>{t.buyer} · Delivery: <b>{t.deliveryAreas?.join(' & ')}</b></p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="counter">{days ?? '?'}</div>
          <div className="label">days to closing</div>
          <div className="spacer" style={{ height: 12 }} />
          <button className="btn small" onClick={e => { e.stopPropagation(); go(`tenders/${t.id}`) }}>Open tender →</button>
        </div>
      </div>
    </article>
  )
}

function LiveTable({ rows }: { rows: TenderSummary[] }) {
  return (
    <div className="card flat" style={{ padding: 0, overflowX: 'auto' }}>
      <table className="ledger responsive">
        <thead><tr><th>Notice</th><th>Delivery location</th><th>Buyer (office)</th><th>Closes</th></tr></thead>
        <tbody>
          {rows.map(t => (
            <tr key={t.id} className="clickable" onClick={() => go(`tenders/${t.id}`)}>
              <td style={{ minWidth: 280 }}>
                <a href={`#/tenders/${t.id}`} style={{ textDecoration: 'none', fontWeight: 600 }}>{t.title}</a>
                <div className="mono tiny muted">{t.sourceId}{t.category ? ` · ${t.category}` : ''}</div>
              </td>
              <td className="small" data-label="Delivery">{t.deliveryLocationUnknown ? <Unknown>Delivery location unknown</Unknown> : t.deliveryAreas!.slice(0, 4).join(', ') + (t.deliveryAreas!.length > 4 ? ` +${t.deliveryAreas!.length - 4}` : '')}</td>
              <td className="small muted" data-label="Buyer office">{t.buyer}{t.buyerAddress ? <div className="tiny">{t.buyerAddress}</div> : null}</td>
              <td className="small mono" data-label="Closes" style={{ whiteSpace: 'nowrap' }}>{t.closed ? <span style={{ color: 'var(--signal)' }}>Closed</span> : fmtDate(t.closingAt, false)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function TenderDetailPage({ id }: { id: string }) {
  const { status, toast } = useApp()
  const { data, setData, error, loading, reload } = useLoad(() => api<TenderDetail>(`/tenders/${id}`), [id])
  const [fetching, setFetching] = useState(false)
  const [mode, setMode] = useState<Mode | null>(null)
  const [assessing, setAssessing] = useState(false)
  const [assessError, setAssessError] = useState<unknown>(null)

  if (error) return <ErrorNotice error={error} onRetry={reload} />
  if (loading || !data) return <Loading />
  const t = data.summary
  const effectiveMode: Mode = mode ?? (t.demo ? (status?.defaultAiMode ?? 'FIXTURE') : 'LIVE')

  async function fetchDocs() {
    setFetching(true)
    try {
      setData(await api<TenderDetail>(`/tenders/${id}/documents/fetch`, { method: 'POST' }))
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Document retrieval failed', 'error')
    } finally {
      setFetching(false)
    }
  }

  async function assess() {
    setAssessing(true)
    setAssessError(null)
    try {
      await api(`/tenders/${id}/assessments`, { method: 'POST', json: { mode: effectiveMode } })
      go(`tenders/${id}/assessment`)
    } catch (e) {
      setAssessError(e)
    } finally {
      setAssessing(false)
    }
  }

  return (
    <>
      <header className="page-head">
        <div>
          <a className="link-btn" href="#/tenders">← All tenders</a>
          <div className="eyebrow" style={{ marginTop: 14 }}>§02 · Tender detail · {t.sourceLabel}</div>
          <h1>{t.title}</h1>
        </div>
        <div className="stack" style={{ justifyItems: 'end' }}>
          <TenderStamp demo={t.demo} />
          {t.closed && <Stamp kind="ai">Closed</Stamp>}
        </div>
      </header>

      <div className="grid sidebar">
        <div className="stack" style={{ gap: 22 }}>
          <section className="card">
            <dl className="kv">
              <dt>Source id</dt><dd className="mono">{t.sourceId}{data.amendmentNumber ? ` · amendment ${data.amendmentNumber}` : ''}</dd>
              <dt>Buyer</dt><dd>{t.buyer ?? <Unknown />}</dd>
              <dt>Buyer office</dt><dd>{t.buyerAddress ?? <Unknown />} <span className="tiny muted">(not the work location)</span></dd>
              <dt>Delivery location</dt><dd>{t.deliveryLocationUnknown ? <Unknown>Delivery location unknown</Unknown> : <b>{t.deliveryAreas!.join(', ')}</b>}</dd>
              <dt>Closing</dt><dd>{fmtDate(t.closingAt)} <div className="tiny muted">Source time basis: {t.sourceTimezone ?? 'unknown'}</div></dd>
              <dt>Status</dt><dd>{t.status ?? <Unknown />}</dd>
              <dt>Category</dt><dd>{t.category ?? <Unknown />}</dd>
              <dt>Fetched</dt><dd className="mono small">{fmtDate(t.fetchedAt)}</dd>
              {data.sourceUrl && <><dt>Source</dt><dd><a href={data.sourceUrl} target="_blank" rel="noreferrer noopener">Official notice ↗</a></dd></>}
            </dl>
          </section>
          <section className="card">
            <div className="card-head"><h2>Notice description</h2></div>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0, maxHeight: 360, overflowY: 'auto' }}>{data.description ?? <Unknown />}</p>
          </section>
        </div>

        <aside className="stack" style={{ gap: 22 }}>
          <section className="card">
            <div className="card-head"><h3>Solicitation documents</h3>
              <span className={`status ${data.documentState === 'COMPLETE' ? 'MATCH' : data.documentState === 'PARTIAL' ? 'MISSING_EVIDENCE' : 'UNKNOWN'}`}>{data.documentState.replace('_', ' ')}</span>
            </div>
            <p className="small" style={{ marginTop: 0 }}>{data.documentStateMessage}</p>
            {data.documents.map(d => (
              <div key={d.id} className="small" style={{ borderTop: '1px solid var(--rule)', padding: '8px 0' }}>
                <div className="row between"><b style={{ wordBreak: 'break-all' }}>{d.name}</b>
                  <span className={`status ${d.accessStatus === 'AVAILABLE' ? 'MATCH' : d.accessStatus === 'EXTRACTION_LIMITED' ? 'MISSING_EVIDENCE' : 'GAP'}`}>{d.accessStatus.replace('_', ' ')}</span></div>
                <div className="tiny muted">{d.accessNote}{d.characters ? ` · ${d.characters.toLocaleString()} characters` : ''}</div>
              </div>
            ))}
            {!t.demo && (
              <button className="btn ghost small" onClick={fetchDocs} disabled={fetching}>
                {fetching ? 'Retrieving…' : data.attachments.length ? `Retrieve ${data.attachments.length} public attachment(s)` : 'Check for documents'}
              </button>
            )}
            {!t.demo && <p className="tiny muted">Only public http(s) documents are downloaded; private networks and sign-in walls are refused.</p>}
          </section>

          <section className="card">
            <div className="card-head"><h3>Gap assessment</h3></div>
            <div className="tabs" role="tablist" aria-label="Analysis mode">
              <button role="tab" aria-selected={effectiveMode === 'FIXTURE'} disabled={!t.demo} onClick={() => setMode('FIXTURE')}
                title={t.demo ? '' : 'Demonstration analysis only applies to the sample scenario'}>Demonstration</button>
              <button role="tab" aria-selected={effectiveMode === 'LIVE'} disabled={!status?.liveAiAvailable} onClick={() => setMode('LIVE')}>Live AI</button>
            </div>
            <p className="small" style={{ marginTop: 0 }}>
              {effectiveMode === 'FIXTURE'
                ? 'Uses the bundled demonstration analysis of the sample document. Capacity and evidence are still recalculated from current profiles.'
                : `Sends the document text (not your contact details) to ${status?.model ?? 'the model'} for structured interpretation. Hours and qualifications are compared deterministically.`}
            </p>
            {data.documentState !== 'COMPLETE' && <div className="notice warn small" style={{ marginBottom: 12 }}><span className="glyph">◆</span><div>Document unavailable / assessment incomplete: no complete readiness verdict will be given.</div></div>}
            {!status?.liveAiAvailable && !t.demo && <div className="notice info small" style={{ marginBottom: 12 }}><span className="glyph">i</span><div>Live AI is not configured (ANTHROPIC_API_KEY). Demonstration analysis is never substituted for a real tender.</div></div>}
            {assessError != null && <div style={{ marginBottom: 12 }}><ErrorNotice error={assessError} /></div>}
            <div className="stack">
              <button className="btn signal block" onClick={assess} disabled={assessing || (effectiveMode === 'LIVE' && !status?.liveAiAvailable)}>
                {assessing ? (effectiveMode === 'LIVE' ? 'Claude is reading…' : 'Assessing…') : 'Run gap assessment'}
              </button>
              <a className="link-btn" href={`#/tenders/${id}/assessment`}>View latest assessment →</a>
            </div>
          </section>
        </aside>
      </div>
    </>
  )
}
