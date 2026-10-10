import type { FindingStatus } from '../types'

const LABELS: Record<FindingStatus, string> = {
  MATCH: 'MATCH',
  GAP: 'GAP',
  MISSING_EVIDENCE: 'MISSING EVIDENCE',
  UNKNOWN: 'UNKNOWN',
}

/** Status shown as a navigation buoy: shape and text, never colour alone. */
export function Buoy({ status, size = 14 }: { status: FindingStatus; size?: number }) {
  const s = size
  switch (status) {
    case 'MATCH':
      return <svg width={s} height={s} viewBox="0 0 14 14" aria-hidden="true"><circle cx="7" cy="7" r="6" fill="currentColor" /></svg>
    case 'GAP':
      return (
        <svg width={s} height={s} viewBox="0 0 14 14" aria-hidden="true">
          <path d="M7 1 L13 13 H1 Z" fill="currentColor" />
          <path d="M7 5.5 V9" stroke="var(--paper-raised)" strokeWidth="1.6" />
        </svg>
      )
    case 'MISSING_EVIDENCE':
      return (
        <svg width={s} height={s} viewBox="0 0 14 14" aria-hidden="true">
          <path d="M7 1 L13 7 L7 13 L1 7 Z" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <path d="M7 1 L13 7 L1 7 Z" fill="currentColor" />
        </svg>
      )
    default:
      return (
        <svg width={s} height={s} viewBox="0 0 14 14" aria-hidden="true">
          <circle cx="7" cy="7" r="5.6" fill="none" stroke="currentColor" strokeWidth="1.6" strokeDasharray="2.2 1.8" />
        </svg>
      )
  }
}

export function StatusMark({ status, conditional }: { status: FindingStatus; conditional?: boolean }) {
  return (
    <span className={`status ${status}`}>
      <Buoy status={status} />
      {LABELS[status]}
      {conditional ? ' · COND.' : ''}
    </span>
  )
}

export function Stamp({ kind, children, big }: { kind: 'fiction' | 'live' | 'ai' | 'fixture' | 'neutral'; children: React.ReactNode; big?: boolean }) {
  return <span className={`stamp ${kind}${big ? ' big' : ''}`}>{children}</span>
}

export function TenderStamp({ demo }: { demo: boolean }) {
  return demo ? <Stamp kind="neutral">Sample scenario</Stamp> : <Stamp kind="live">Live · CanadaBuys federal</Stamp>
}

export function ModeStamp({ mode }: { mode: 'LIVE' | 'FIXTURE' }) {
  return mode === 'FIXTURE' ? <Stamp kind="fixture">Demonstration analysis</Stamp> : <Stamp kind="ai">Live AI analysis</Stamp>
}

export function Loading({ label = 'Taking soundings…' }: { label?: string }) {
  return <div className="loading" role="status">{label}</div>
}

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const msg = error instanceof Error ? error.message : String(error)
  return (
    <div className="notice error" role="alert">
      <span className="glyph">⚠</span>
      <div style={{ flex: 1 }}>{msg}</div>
      {onRetry && <button className="btn small ghost" onClick={onRetry}>Retry</button>}
    </div>
  )
}

export function Unknown({ children = 'unknown' }: { children?: React.ReactNode }) {
  return <span className="unknown">{children}</span>
}
