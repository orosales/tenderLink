export type EvidenceStatus = 'EVIDENCED' | 'DECLARED_NO_EVIDENCE' | 'UNKNOWN'
export type FindingStatus = 'MATCH' | 'GAP' | 'MISSING_EVIDENCE' | 'UNKNOWN'
export type Category = 'MANDATORY_ELIGIBILITY' | 'DELIVERY_CONSTRAINT' | 'SCORED_CRITERION'
export type Kind = 'CAPACITY' | 'QUALIFICATION' | 'PARTNERING' | 'OTHER'
export type AppliesTo = 'LEAD_BIDDER' | 'EACH_DELIVERY_COMPANY' | 'TEAM_COMBINED' | 'UNKNOWN'
export type Permission = 'PERMITTED_WITH_CONDITIONS' | 'PROHIBITED' | 'UNKNOWN'
export type Mode = 'LIVE' | 'FIXTURE'
export type InvitationState = 'INVITED' | 'INTEREST_ACCEPTED' | 'DECLINED' | 'CONFIRMED_FOR_ASSESSMENT' | 'WITHDRAWN'

export interface CapacityEntry { area: string; service: string; weeklyHours: number }
export interface Qualification { name: string; status: EvidenceStatus; evidenceNote?: string | null }

export interface CompanySummary {
  id: string; name: string; openToPartnerships: boolean; confirmed: boolean; demo: boolean; serviceAreas: string[]
}

export interface CompanyProfile {
  id: string; name: string; contactName: string | null; services: string[]; specializations: string[]
  serviceAreas: string[]; capacity: CapacityEntry[]; experience: string | null; qualifications: Qualification[]
  evidenceSummary: string | null; partnershipPreference: string | null; openToPartnerships: boolean
  profileConfirmedAt: string | null; profileRevision: number; updatedAt: string; demo: boolean; ownProfile: boolean
}

export interface SystemStatus {
  demoMode: boolean; demoModeNotice: string; defaultAiMode: Mode; liveAiAvailable: boolean; model: string
  feedUrl: string; importScheduleEnabled: boolean; lastImport: ImportRun | null
  demoCompanyIds: string[]; demoTenderId: string
}

export interface ImportRun {
  id: string; status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'; trigger: string; url: string; startedAt: string
  finishedAt: string | null; rowsRead: number; inserted: number; updated: number; unchanged: number; skipped: number
  error: string | null
}

export interface TenderSummary {
  id: string; source: string; sourceLabel: string; sourceId: string; title: string; buyer: string | null
  buyerAddress: string | null; deliveryAreas: string[] | null; deliveryLocationUnknown: boolean; category: string | null
  closingAt: string | null; sourceTimezone: string | null; closed: boolean; status: string | null; fetchedAt: string
  demo: boolean
}

export interface TenderList {
  demo: TenderSummary[]; live: TenderSummary[]; liveMatching: number; liveTotal: number
  lastImport: ImportRun | null; lastSuccessfulImport: ImportRun | null; liveSourceLabel: string
}

export interface DocumentView {
  id: string; name: string; url: string | null; accessStatus: 'AVAILABLE' | 'UNAVAILABLE' | 'EXTRACTION_LIMITED'
  accessNote: string | null; versionHash: string | null; characters: number; fetchedAt: string; demo: boolean
}

export interface TenderDetail {
  summary: TenderSummary; solicitationNumber: string | null; amendmentNumber: string | null; description: string | null
  sourceUrl: string | null; deliveryRegionsRaw: string | null; attachments: string[]; publishedOn: string | null
  documents: DocumentView[]; documentState: 'COMPLETE' | 'PARTIAL' | 'NOT_RETRIEVED' | 'UNAVAILABLE'
  documentStateMessage: string
}

export interface Requirement {
  id: string; description: string; category: Category; kind: Kind; appliesTo: AppliesTo; area: string | null
  service: string | null; weeklyHours: number | null; qualificationName: string | null; matchTerms: string[]
  sourceReference: string | null; sourceExcerpt: string | null; excerptVerified: boolean; humanReviewed: boolean
  validationNotes: string[]
}

export interface Deadline { label: string; value: string; sourceReference: string | null; sourceExcerpt: string | null; excerptVerified: boolean }

export interface Extraction {
  documentComplete: boolean; documentCompletenessNote: string | null; scopeSummary: string | null; deadlines: Deadline[]
  requirements: Requirement[]; partnershipPermission: Permission; partnershipArrangement: string | null
  partnershipSourceReference: string | null; partnershipExcerpt: string | null; partnershipExcerptVerified: boolean
  partnershipConditions: string[]; jointVentureNote: string | null; unknowns: string[]
}

export interface MemberEvaluation {
  companyId: string; companyName: string; role: string; status: FindingStatus; hoursCounted: number | null; note: string
}

export interface Finding {
  requirementId: string; description: string; category: Category; kind: Kind; appliesTo: AppliesTo
  sourceReference: string | null; sourceExcerpt: string | null; excerptVerified: boolean; humanReviewed: boolean
  status: FindingStatus; conditional: boolean; companyEvidence: string; explanation: string; action: string
  members: MemberEvaluation[]
}

export interface ActionItem { priority: number; text: string; requirementId: string | null }

export interface Comparison {
  requirementId: string; description: string; before: FindingStatus | null; beforeConditional: boolean
  after: FindingStatus; afterConditional: boolean; changed: boolean
}

export interface Assessment {
  id: string; tenderId: string; tenderTitle: string; tenderDemo: boolean; company: { id: string; name: string }
  partners: { id: string; name: string }[]; invitationId: string | null; combined: boolean; mode: Mode
  modeLabel: string; model: string; documentComplete: boolean; documentSources: string[]; limitations: string[]
  documentVersion: string; readiness: 'INCOMPLETE_ASSESSMENT' | 'GAPS_TO_RESOLVE' | 'CONDITIONS_OUTSTANDING' | 'NO_GAPS_IN_ASSESSED_REQUIREMENTS'
  readinessLabel: string; extraction: Extraction; findings: Finding[]; actions: ActionItem[]; warnings: string[]
  createdAt: string; stale: boolean; staleReasons: string[]; baselineAssessmentId: string | null
  comparison: Comparison[] | null
}

export interface ProposedContribution {
  area: string; service: string; weeklyHours: number; partnerRole: string; leadRole: string
  responsibilities: string | null; addressesRequirementIds: string[]
}

export interface GapCoverage {
  requirementId: string; area: string; service: string | null; shortfall: number; declaredHours: number
  suggestedHours: number; fullyCovers: boolean
}

export interface Recommendation {
  candidate: {
    companyId: string; companyName: string; services: string[]; serviceAreas: string[]
    addresses: GapCoverage[]; credentials: { requirementId: string; requirement: string; status: FindingStatus; note: string }[]
    coverageRatio: number; label: string
  }
  partnershipPreference: string | null; explanation: string; explanationSource: string
  suggestedContribution: ProposedContribution
  existingInvitation: { id: string; state: InvitationState; direction: string } | null
}

export interface Recommendations {
  assessmentId: string; recalculatedFromCurrentProfile: boolean; disclaimer: string; partnershipPermission: Permission
  permissionLabel: string; partnershipExcerpt: string | null; partnershipSourceReference: string | null
  partnershipConditions: string[]; jointVentureNote: string | null; feasible: boolean
  gaps: { requirementId: string; description: string; area: string; service: string | null; requiredHours: number; leadHours: number; shortfall: number }[]
  recommendations: Recommendation[]; excludedOptedOutOrUnconfirmed: number
  unresolvedForLead: { requirementId: string; description: string; status: FindingStatus; note: string }[]
  alternatives: string[]
}

export interface Invitation {
  id: string; tenderId: string; tenderTitle: string; tenderDemo: boolean; sender: { id: string; name: string }
  recipient: { id: string; name: string }; proposedContribution: ProposedContribution; message: string | null
  state: InvitationState; senderConfirmedAt: string | null; recipientConfirmedAt: string | null
  respondedAt: string | null; createdAt: string; updatedAt: string; viewerRole: 'SENDER' | 'RECIPIENT'
  availableActions: string[]; latestCombinedAssessmentId: string | null
}
