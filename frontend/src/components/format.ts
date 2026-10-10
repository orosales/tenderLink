export function fmtDate(iso: string | null | undefined, withTime = true): string {
  if (!iso) return 'unknown'
  const d = new Date(iso)
  return d.toLocaleString('en-CA', {
    timeZone: 'America/Halifax',
    year: 'numeric', month: 'short', day: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', timeZoneName: 'short' } : {}),
  })
}

export function daysUntil(iso: string | null | undefined): number | null {
  if (!iso) return null
  return Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000)
}

export const CATEGORY_LABEL: Record<string, string> = {
  MANDATORY_ELIGIBILITY: 'Mandatory eligibility',
  DELIVERY_CONSTRAINT: 'Delivery constraint',
  SCORED_CRITERION: 'Scored criterion',
}

export const APPLIES_LABEL: Record<string, string> = {
  LEAD_BIDDER: 'Lead bidder',
  EACH_DELIVERY_COMPANY: 'Each delivery company',
  TEAM_COMBINED: 'Team, per location',
  UNKNOWN: 'Applies to: unknown',
}

export const PERMISSION_LABEL: Record<string, string> = {
  PERMITTED_WITH_CONDITIONS: 'Permitted with conditions',
  PROHIBITED: 'Prohibited',
  UNKNOWN: 'Unknown',
}

export const STATE_LABEL: Record<string, string> = {
  INVITED: 'Invited',
  INTEREST_ACCEPTED: 'Interest accepted',
  DECLINED: 'Declined',
  CONFIRMED_FOR_ASSESSMENT: 'Confirmed for assessment',
  WITHDRAWN: 'Withdrawn',
}
