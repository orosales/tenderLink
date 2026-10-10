import { useEffect, useState } from 'react'
import { useApp, readPref, writePref } from '../context'
import { Compass, FLAG_NAMES, SignalFlag } from './Flag'

const NAV = [
  { key: 'profile', num: '01', label: 'Company profile' },
  { key: 'tenders', num: '02', label: 'Tenders' },
  { key: 'assessment', num: '03', label: 'Assessment' },
  { key: 'collaboration', num: '04', label: 'Collaboration' },
]

type Theme = 'auto' | 'light' | 'dark'

export function Shell({ section, children, assessmentHref }: { section: string; children: React.ReactNode; assessmentHref: string }) {
  const { companies, activeId, setActive, flag, status, pendingInvitations } = useApp()
  const [theme, setTheme] = useState<Theme>(() => (readPref('theme') as Theme) ?? 'auto')

  useEffect(() => {
    if (theme === 'auto') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', theme)
    writePref('theme', theme)
  }, [theme])

  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 860px)').matches)
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 860px)')
    const on = () => setMobile(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])

  const order = status?.demoCompanyIds ?? []
  const demoCompanies = companies.filter(c => c.demo).sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id))

  return (
    <>
      <div className="hazard" role="note">
        <span>Demo mode: simulated company access</span>
        <span>Not authentication</span>
      </div>
      <div className="shell">
        <aside className="rail">
          <a className="brand" href="#/profile">
            <Compass size={46} />
            <div>
              <div className="brand-name">TenderReady</div>
              <div className="brand-sub">Nova Scotia · Fusion3</div>
            </div>
          </a>

          <div>
            <div className="rail-label"><span>Hoist a company</span><span /></div>
            <div className="hoist">
              {demoCompanies.map(c => (
                <button key={c.id} className="hoist-btn" aria-pressed={c.id === activeId} onClick={() => setActive(c.id)}
                  title="Switch the simulated active company" aria-label={`${c.name}${c.id === activeId ? ' (active)' : ''}`}>
                  <span className="flag-wrap"><SignalFlag kind={flag(c.id)} size={46} /></span>
                  <span>
                    <div className="hoist-name">{c.name}</div>
                    <div className="hoist-meta">Flag {FLAG_NAMES[flag(c.id)]} · {c.serviceAreas.join(', ') || 'areas unknown'}</div>
                  </span>
                </button>
              ))}
            </div>
          </div>

          <nav className="nav" aria-label="Main">
            {NAV.map(n => (
              <a key={n.key} href={n.key === 'assessment' ? assessmentHref : `#/${n.key}`} aria-current={section === n.key ? 'page' : undefined}>
                <span className="nav-num">{n.num}</span>
                <span className="nav-text">{n.label}</span>
                {n.key === 'collaboration' && pendingInvitations > 0 ? <span className="nav-badge" aria-label={`${pendingInvitations} awaiting action`}>{pendingInvitations}</span> : <span />}
              </a>
            ))}
          </nav>

          <details className="rail-foot" open={!mobile} key={String(mobile)}>
            <summary>Status &amp; theme</summary>
            <div className="row"><span>AI analysis</span><b>{status ? (status.defaultAiMode === 'LIVE' ? 'Live' : 'Fixture default') : '…'}</b></div>
            <div className="row"><span>Live AI key</span><b>{status ? (status.liveAiAvailable ? 'configured' : 'not configured') : '…'}</b></div>
            <div className="row"><span>Feed</span><b>CanadaBuys federal</b></div>
            <div className="theme-toggle" role="group" aria-label="Theme">
              {(['light', 'auto', 'dark'] as Theme[]).map(t => (
                <button key={t} aria-pressed={theme === t} onClick={() => setTheme(t)}>
                  {t === 'light' ? 'Day chart' : t === 'dark' ? 'Night watch' : 'Auto'}
                </button>
              ))}
            </div>
          </details>
        </aside>
        <main className="main">{children}</main>
      </div>
    </>
  )
}
