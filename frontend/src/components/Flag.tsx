// International maritime signal flags, used as each demo company's identity.
// Alfa (white/blue swallowtail), Bravo (red swallowtail), Charlie (striped) for anything else.

export type FlagKind = 'alfa' | 'bravo' | 'charlie'

export function flagFor(companyId: string | undefined, demoIds: string[]): FlagKind {
  if (!companyId) return 'charlie'
  const i = demoIds.indexOf(companyId)
  return i === 0 ? 'alfa' : i === 1 ? 'bravo' : 'charlie'
}

export const FLAG_NAMES: Record<FlagKind, string> = { alfa: 'Alfa', bravo: 'Bravo', charlie: 'Charlie' }

export function SignalFlag({ kind, size = 40, title }: { kind: FlagKind; size?: number; title?: string }) {
  const h = size * 0.7
  return (
    <svg className="flag" width={size} height={h} viewBox="0 0 40 28" role="img" aria-label={title ?? `Signal flag ${FLAG_NAMES[kind]}`}>
      {kind === 'alfa' && (
        <>
          <path d="M0 0H40L32 14L40 28H0Z" fill="#1d4f91" />
          <rect x="0" y="0" width="17" height="28" fill="#fbf7ee" />
          <path d="M0 0H40L32 14L40 28H0Z" fill="none" stroke="#0b1622" strokeWidth="1" />
        </>
      )}
      {kind === 'bravo' && <path d="M0 0H40L32 14L40 28H0Z" fill="#d13a26" stroke="#0b1622" strokeWidth="1" />}
      {kind === 'charlie' && (
        <>
          <rect width="40" height="28" fill="#1d4f91" />
          <rect y="5.6" width="40" height="16.8" fill="#fbf7ee" />
          <rect y="9.8" width="40" height="8.4" fill="#d13a26" />
          <rect width="40" height="28" fill="none" stroke="#0b1622" strokeWidth="1" />
        </>
      )}
    </svg>
  )
}

export function Compass({ size = 44 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true">
      <circle cx="50" cy="50" r="46" fill="none" stroke="currentColor" strokeWidth="1.5" opacity="0.6" />
      <circle cx="50" cy="50" r="38" fill="none" stroke="currentColor" strokeWidth="0.75" strokeDasharray="2 3" opacity="0.6" />
      <path d="M50 4 L58 50 L50 96 L42 50Z" fill="currentColor" opacity="0.9" />
      <path d="M4 50 L50 42 L96 50 L50 58Z" fill="currentColor" opacity="0.45" />
      <path d="M50 4 L58 50 L50 50Z" fill="#e7b53c" />
      <circle cx="50" cy="50" r="4" fill="#e7b53c" />
      <text x="50" y="-1" fontSize="12" textAnchor="middle" fill="currentColor" fontFamily="IBM Plex Mono">N</text>
    </svg>
  )
}
