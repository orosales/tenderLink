import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setActiveCompanyId } from './api'
import { flagFor, type FlagKind } from './components/Flag'
import type { CompanySummary, Invitation, SystemStatus } from './types'

// ---------- tiny hash router ----------
export function useRoute(): string[] {
  const [hash, setHash] = useState(() => window.location.hash)
  useEffect(() => {
    const on = () => setHash(window.location.hash)
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  const path = hash.replace(/^#\/?/, '')
  return path ? path.split('/') : ['profile']
}

export function go(path: string) {
  window.location.hash = '#/' + path.replace(/^\//, '')
  window.scrollTo({ top: 0 })
}

// ---------- per-viewer conveniences (never authoritative) ----------
export function readPref(key: string): string | null {
  try {
    return window.localStorage.getItem('tenderready.' + key)
  } catch {
    return null
  }
}
export function writePref(key: string, value: string) {
  try {
    window.localStorage.setItem('tenderready.' + key, value)
  } catch {
    /* storage unavailable: preference simply isn't remembered */
  }
}

// ---------- app context ----------
interface Ctx {
  status: SystemStatus | null
  companies: CompanySummary[]
  activeId: string | null
  active: CompanySummary | null
  setActive: (id: string) => void
  flag: (companyId?: string) => FlagKind
  refreshCompanies: () => Promise<void>
  pendingInvitations: number
  refreshInvitations: () => Promise<void>
  toast: (msg: string, tone?: 'ok' | 'error' | 'info') => void
  bootError: unknown
  version: number
}

const AppContext = createContext<Ctx | null>(null)

export function useApp(): Ctx {
  const c = useContext(AppContext)
  if (!c) throw new Error('AppContext missing')
  return c
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<SystemStatus | null>(null)
  const [companies, setCompanies] = useState<CompanySummary[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [pending, setPending] = useState(0)
  const [toastMsg, setToastMsg] = useState<{ msg: string; tone: string } | null>(null)
  const [bootError, setBootError] = useState<unknown>(null)
  const [version, setVersion] = useState(0)

  const refreshCompanies = useCallback(async () => {
    setCompanies(await api<CompanySummary[]>('/companies'))
  }, [])

  const refreshInvitations = useCallback(async () => {
    try {
      const list = await api<Invitation[]>('/invitations')
      setPending(list.filter(i => i.availableActions.some(a => a !== 'WITHDRAW' && a !== 'REASSESS')).length)
    } catch {
      setPending(0)
    }
  }, [])

  useEffect(() => {
    ;(async () => {
      try {
        const s = await api<SystemStatus>('/system/status')
        setStatus(s)
        const list = await api<CompanySummary[]>('/companies')
        setCompanies(list)
        const remembered = readPref('activeCompany')
        const initial = list.find(c => c.id === remembered && c.demo)?.id ?? s.demoCompanyIds[0] ?? null
        setActiveCompanyId(initial)
        setActiveId(initial)
      } catch (e) {
        setBootError(e)
      }
    })()
  }, [])

  useEffect(() => {
    if (activeId) refreshInvitations()
  }, [activeId, refreshInvitations, version])

  const setActive = useCallback((id: string) => {
    setActiveCompanyId(id)
    setActiveId(id)
    writePref('activeCompany', id)
    setVersion(v => v + 1)
  }, [])

  const toast = useCallback((msg: string, tone: 'ok' | 'error' | 'info' = 'ok') => {
    setToastMsg({ msg, tone })
    window.setTimeout(() => setToastMsg(t => (t?.msg === msg ? null : t)), 4200)
  }, [])

  const value = useMemo<Ctx>(() => ({
    status,
    companies,
    activeId,
    active: companies.find(c => c.id === activeId) ?? null,
    setActive,
    flag: (id?: string) => flagFor(id, status?.demoCompanyIds ?? []),
    refreshCompanies,
    pendingInvitations: pending,
    refreshInvitations,
    toast,
    bootError,
    version,
  }), [status, companies, activeId, setActive, refreshCompanies, pending, refreshInvitations, toast, bootError, version])

  return (
    <AppContext.Provider value={value}>
      {children}
      {toastMsg && (
        <div className={`toast notice ${toastMsg.tone === 'error' ? 'error' : toastMsg.tone === 'info' ? 'info' : 'ok'}`} role="status">
          <span className="glyph">{toastMsg.tone === 'error' ? '⚠' : '⚓'}</span>
          <div>{toastMsg.msg}</div>
        </div>
      )}
    </AppContext.Provider>
  )
}

/** Generic loader hook: re-runs when deps change. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let alive = true
    setLoading(true)
    setError(null)
    fn().then(d => { if (alive) setData(d) }, e => { if (alive) setError(e) }).finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])
  return { data, setData, error, loading, reload: () => setTick(t => t + 1) }
}
