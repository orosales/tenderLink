import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'
import { AppProvider, readPref, useApp, useRoute, writePref } from './context'
import { Shell } from './components/Shell'
import { ErrorNotice, Loading } from './components/Marks'
import { ProfilePage } from './pages/ProfilePage'
import { TenderDetailPage, TendersPage } from './pages/TendersPage'
import { AssessmentPage } from './pages/AssessmentPage'
import { CollaborationPage, InvitationDetailPage } from './pages/CollaborationPage'

function Root() {
  const route = useRoute()
  const { bootError, activeId, status, version } = useApp()
  const [section, id, sub] = route

  useEffect(() => {
    if (section === 'tenders' && id && sub === 'assessment') writePref('lastTender', id)
  }, [section, id, sub])

  const lastTender = readPref('lastTender') ?? status?.demoTenderId
  const assessmentHref = lastTender ? `#/tenders/${lastTender}/assessment` : '#/tenders'
  const nav = section === 'tenders' && sub === 'assessment' ? 'assessment' : section

  let page: React.ReactNode
  if (bootError) page = <ErrorNotice error={bootError} onRetry={() => window.location.reload()} />
  else if (!activeId) page = <Loading label="Hoisting flags…" />
  else if (section === 'tenders' && id && sub === 'assessment') page = <AssessmentPage key={`${id}-${version}`} tenderId={id} />
  else if (section === 'tenders' && id) page = <TenderDetailPage key={id} id={id} />
  else if (section === 'tenders') page = <TendersPage />
  else if (section === 'collaboration' && id) page = <InvitationDetailPage key={id} id={id} />
  else if (section === 'collaboration') page = <CollaborationPage />
  else page = <ProfilePage />

  return <Shell section={nav} assessmentHref={assessmentHref}>{page}</Shell>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProvider>
      <Root />
    </AppProvider>
  </StrictMode>,
)
