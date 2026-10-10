import { useId } from 'react'
import type { FlagKind } from './Flag'

// A stylised nautical chart of Nova Scotia. Coastline points are approximate (lat, lon) and only decorative;
// the data on top (required vs. covered hours per location) is exact.

type LatLon = [number, number]

const MAINLAND: LatLon[] = [
  [43.84, -66.12], [44.05, -66.2], [44.3, -66.06], [44.62, -65.76], [44.8, -65.45], [45.02, -65.05],
  [45.24, -64.62], [45.33, -64.5], [45.16, -64.38], [45.07, -64.2], [45.2, -63.9], [45.33, -63.55],
  [45.36, -63.33], [45.4, -63.62], [45.39, -64.2], [45.36, -64.6], [45.33, -64.95], [45.55, -64.75],
  [45.78, -64.38], [45.97, -64.0], [45.85, -63.62], [45.74, -63.25], [45.72, -62.95], [45.68, -62.7],
  [45.72, -62.2], [45.87, -61.92], [45.63, -61.45], [45.33, -61.02], [45.17, -61.42], [44.92, -62.53],
  [44.76, -63.15], [44.6, -63.45], [44.48, -63.75], [44.38, -64.3], [44.2, -64.62], [43.95, -64.95],
  [43.76, -65.32], [43.47, -65.62], [43.62, -65.9],
]

const CAPE_BRETON: LatLon[] = [
  [45.62, -61.36], [45.98, -61.5], [46.3, -61.36], [46.68, -60.98], [47.03, -60.6], [46.86, -60.32],
  [46.42, -60.3], [46.22, -59.98], [45.92, -59.97], [45.68, -60.38], [45.52, -60.92],
]

export const PLACES: Record<string, LatLon> = {
  halifax: [44.65, -63.57], truro: [45.36, -63.28], dartmouth: [44.67, -63.57], sydney: [46.14, -60.19],
  yarmouth: [43.84, -66.12], kentville: [45.08, -64.5], wolfville: [45.09, -64.36], antigonish: [45.62, -61.99],
  'new glasgow': [45.59, -62.65], amherst: [45.83, -64.21], lunenburg: [44.38, -64.31], bridgewater: [44.38, -64.52],
  greenwood: [44.97, -64.93], digby: [44.62, -65.76], pictou: [45.68, -62.71], shelburne: [43.76, -65.32],
  'port hawkesbury': [45.62, -61.35], windsor: [44.99, -64.13], bedford: [44.73, -63.66],
}

const W = 900
const H = 690
const LON0 = -66.6
const LAT0 = 47.25
const K = 175

function project([lat, lon]: LatLon): [number, number] {
  return [(lon - LON0) * Math.cos((45 * Math.PI) / 180) * K + 20, (LAT0 - lat) * K + 10]
}

function smoothPath(points: LatLon[]): string {
  const p = points.map(project)
  const n = p.length
  let d = `M${p[0][0].toFixed(1)},${p[0][1].toFixed(1)}`
  for (let i = 0; i < n; i++) {
    const p0 = p[(i - 1 + n) % n], p1 = p[i], p2 = p[(i + 1) % n], p3 = p[(i + 2) % n]
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6]
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6]
    d += ` C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`
  }
  return d + 'Z'
}

const SOUNDINGS: [number, number, string][] = [
  [700, 560, '64'], [760, 480, '81'], [620, 620, '57'], [520, 640, '43'], [380, 640, '38'], [240, 620, '29'],
  [100, 560, '22'], [60, 420, '17'], [160, 210, '12'], [300, 120, '9'], [470, 80, '24'], [560, 150, '31'],
  [820, 300, '96'], [840, 620, '112'], [700, 380, '72'], [440, 560, '51'],
]

export interface ChartSite {
  area: string
  required: number
  segments: { hours: number; flag: FlagKind; label: string }[]
  status: 'MATCH' | 'GAP' | 'MISSING_EVIDENCE' | 'UNKNOWN'
  conditional?: boolean
}

export interface ChartFlag {
  area: string
  flag: FlagKind
  label: string
}

const FLAG_COLOR: Record<FlagKind, string> = { alfa: '#1d4f91', bravo: '#d13a26', charlie: '#6b5aa6' }

function arc(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const p = (a: number) => [cx + r * Math.sin(a), cy - r * Math.cos(a)]
  const [x0, y0] = p(a0)
  const [x1, y1] = p(a1)
  const large = a1 - a0 > Math.PI ? 1 : 0
  return `M${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1}`
}

export function NovaScotiaChart({ sites = [], flags = [], caption }: { sites?: ChartSite[]; flags?: ChartFlag[]; caption?: string }) {
  const uid = useId().replace(/:/g, '')
  const known = sites.filter(s => PLACES[s.area.toLowerCase()])
  const unplaced = sites.filter(s => !PLACES[s.area.toLowerCase()])
  const summary = sites.map(s => {
    const got = s.segments.reduce((a, b) => a + b.hours, 0)
    return `${s.area}: ${got} of ${s.required} hours per week, ${s.status === 'GAP' ? `gap of ${s.required - got} hours` : s.conditional ? 'covered, conditional' : s.status === 'MATCH' ? 'covered' : s.status.toLowerCase()}`
  }).join('; ')
  return (
    <figure className="chart-frame" style={{ margin: 0 }}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${caption ?? 'Chart of Nova Scotia delivery locations'}${summary ? '. ' + summary : ''}`}>
        <defs>
          <pattern id={`hatch-${uid}`} width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <line x1="0" y1="0" x2="0" y2="7" stroke="var(--signal)" strokeWidth="2" opacity="0.55" />
          </pattern>
          <filter id={`paper-${uid}`}>
            <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="3" />
            <feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.05 0" />
            <feComposite in2="SourceGraphic" operator="in" />
          </filter>
        </defs>

        {/* graticule */}
        {[44, 45, 46, 47].map(lat => {
          const y = project([lat, LON0])[1]
          return (
            <g key={`lat${lat}`}>
              <line x1="0" x2={W} y1={y} y2={y} stroke="var(--sea-line)" strokeWidth="0.8" strokeDasharray="1 5" />
              <text x="8" y={y - 5} fontSize="12" fill="var(--sea-line)" fontFamily="IBM Plex Mono">{lat}°00′N</text>
            </g>
          )
        })}
        {[-66, -65, -64, -63, -62, -61, -60].map(lon => {
          const x = project([LAT0, lon])[0]
          return (
            <g key={`lon${lon}`}>
              <line y1="0" y2={H} x1={x} x2={x} stroke="var(--sea-line)" strokeWidth="0.8" strokeDasharray="1 5" />
              <text x={x + 5} y={H - 8} fontSize="12" fill="var(--sea-line)" fontFamily="IBM Plex Mono">{Math.abs(lon)}°W</text>
            </g>
          )
        })}

        {/* soundings */}
        {SOUNDINGS.map(([x, y, t], i) => (
          <text key={i} x={x} y={y} fontSize="13" fontStyle="italic" fill="var(--sea-line)" fontFamily="Fraunces, serif">{t}</text>
        ))}

        {/* land with a contour echo */}
        {[MAINLAND, CAPE_BRETON].map((pts, i) => (
          <g key={i}>
            <path d={smoothPath(pts)} fill="none" stroke="var(--sea-line)" strokeWidth="10" strokeOpacity="0.35" strokeLinejoin="round" />
            <path d={smoothPath(pts)} fill="none" stroke="var(--sea-line)" strokeWidth="1" strokeDasharray="3 4" transform="translate(0,0) scale(1)" opacity="0.8" />
            <path d={smoothPath(pts)} fill="var(--land)" stroke="var(--land-edge)" strokeWidth="1.6" />
            <path d={smoothPath(pts)} fill="#000" filter={`url(#paper-${uid})`} />
          </g>
        ))}
        <text {...xy(project([45.0, -63.9]))} fontSize="22" letterSpacing="9" fill="var(--land-edge)" fontFamily="Fraunces, serif" fontStyle="italic" opacity="0.75">NOVA SCOTIA</text>
        <text {...xy(project([46.55, -64.6]))} fontSize="14" letterSpacing="5" fill="var(--sea-line)" fontFamily="IBM Plex Mono">NORTHUMBERLAND STR.</text>
        <text {...xy(project([44.9, -66.4]))} fontSize="14" letterSpacing="5" fill="var(--sea-line)" fontFamily="IBM Plex Mono" transform={`rotate(-48 ${project([44.9, -66.4]).join(' ')})`}>BAY OF FUNDY</text>
        <text {...xy(project([43.75, -63.4]))} fontSize="15" letterSpacing="7" fill="var(--sea-line)" fontFamily="IBM Plex Mono">ATLANTIC OCEAN</text>

        {/* compass rose */}
        <g transform={`translate(${W - 110} 110)`} opacity="0.85">
          <circle r="64" fill="none" stroke="var(--ink)" strokeWidth="1" opacity="0.5" />
          <circle r="54" fill="none" stroke="var(--ink)" strokeWidth="0.6" strokeDasharray="2 3" opacity="0.5" />
          {Array.from({ length: 32 }, (_, i) => (
            <line key={i} x1="0" y1={-64} x2="0" y2={i % 4 === 0 ? -54 : -59} stroke="var(--ink)" strokeWidth="0.8" opacity="0.6" transform={`rotate(${i * 11.25})`} />
          ))}
          <path d="M0 -50 L8 0 L0 50 L-8 0Z" fill="var(--ink)" />
          <path d="M-50 0 L0 -8 L50 0 L0 8Z" fill="var(--ink)" opacity="0.45" />
          <path d="M0 -50 L8 0 L0 0Z" fill="#e7b53c" />
          <text y="-70" textAnchor="middle" fontSize="14" fill="var(--ink)" fontFamily="IBM Plex Mono">N</text>
        </g>

        {/* company presence flags */}
        {flags.filter(f => PLACES[f.area.toLowerCase()]).map((f, i) => {
          const [x, y] = project(PLACES[f.area.toLowerCase()])
          const same = flags.slice(0, i).filter(o => o.area.toLowerCase() === f.area.toLowerCase()).length
          return (
            <g key={`f${i}`} transform={`translate(${x + same * 36} ${y - 46})`}>
              <circle cx="0" cy="46" r="4" fill="var(--ink)" />
              <line x1="0" y1="0" x2="0" y2="46" stroke="var(--ink)" strokeWidth="1.8" />
              <g transform="translate(1 0)"><SvgFlag kind={f.flag} /></g>
              <text x="8" y="64" fontSize="14" fill="var(--ink)" fontFamily="IBM Plex Mono" fontWeight="600">{f.area.toUpperCase()}</text>
              <text x="8" y="80" fontSize="12" fill="var(--ink-2)" fontFamily="IBM Plex Mono">{f.label}</text>
            </g>
          )
        })}

        {/* delivery sites */}
        {known.map((s, i) => {
          const [x, y] = project(PLACES[s.area.toLowerCase()])
          const r = 26 + Math.min(34, Math.sqrt(s.required) * 3.4)
          const total = s.segments.reduce((a, b) => a + b.hours, 0)
          let acc = 0
          const gap = s.status === 'GAP'
          const lonOf = PLACES[s.area.toLowerCase()][1]
          // Sites west of Truro (Annapolis Valley, South Shore) label to the left so they never collide with Truro/Halifax.
          const labelLeft = x > W * 0.55 || lonOf < -64.2
          const lx = labelLeft ? x - r - 18 : x + r + 18
          return (
            <g key={i}>
              {gap && <circle cx={x} cy={y} r={r} fill="var(--signal)" className="pulse" opacity="0.5" />}
              <circle cx={x} cy={y} r={r} fill={gap ? `url(#hatch-${uid})` : 'var(--paper-raised)'} stroke="var(--ink)" strokeWidth="1.5" opacity="0.95" />
              <circle cx={x} cy={y} r={r - 9} fill="var(--paper-raised)" stroke="var(--ink)" strokeWidth="0.6" strokeDasharray="2 2" />
              {s.segments.map((seg, j) => {
                if (seg.hours <= 0) return null
                const a0 = (acc / Math.max(s.required, total)) * Math.PI * 2
                acc += seg.hours
                const a1 = Math.min(Math.PI * 2 - 0.001, (acc / Math.max(s.required, total)) * Math.PI * 2)
                return <path key={j} d={arc(x, y, r - 4.5, a0, a1)} stroke={FLAG_COLOR[seg.flag]} strokeWidth="9" fill="none" strokeDasharray={s.conditional && j > 0 ? '6 3' : undefined} />
              })}
              <text x={x} y={y + 2} textAnchor="middle" fontSize="19" fontWeight="600" fill="var(--ink)" fontFamily="Fraunces, serif">{total}/{s.required}</text>
              <text x={x} y={y + 16} textAnchor="middle" fontSize="9.5" fill="var(--ink-3)" fontFamily="IBM Plex Mono">H/WEEK</text>
              <circle cx={x} cy={y} r="2.5" fill="var(--ink)" />
              <line x1={labelLeft ? x - r : x + r} y1={y} x2={labelLeft ? lx + 4 : lx - 4} y2={y} stroke="var(--ink)" strokeWidth="1" />
              <g transform={`translate(${lx} ${y})`} textAnchor={labelLeft ? 'end' : 'start'}>
                <text y="-6" fontSize="17" letterSpacing="3" fill="var(--ink)" fontFamily="IBM Plex Mono" fontWeight="600">{s.area.toUpperCase()}</text>
                <text y="13" fontSize="12.5" fill={gap ? 'var(--signal)' : s.conditional ? 'var(--brass)' : 'var(--kelp)'} fontFamily="IBM Plex Mono" fontWeight="600">
                  {gap ? `GAP · SHORT ${s.required - total} H` : s.conditional ? 'COVERED · CONDITIONAL' : s.status === 'MATCH' ? 'COVERED' : s.status}
                </text>
                {s.segments.filter(seg => seg.hours > 0).map((seg, j) => (
                  <text key={j} y={30 + j * 15} fontSize="11.5" fill="var(--ink-2)" fontFamily="IBM Plex Mono">{seg.label}: {seg.hours} h</text>
                ))}
              </g>
            </g>
          )
        })}
      </svg>
      {sites.length > 0 && (
        <ul className="chart-readout" aria-hidden="true">
          {sites.map(s => {
            const got = s.segments.reduce((a, b) => a + b.hours, 0)
            const tone = s.status === 'GAP' ? 'GAP' : s.conditional ? 'MISSING_EVIDENCE' : s.status
            return (
              <li key={s.area} className={`status ${tone}`}>
                <b>{s.area}</b> {got}/{s.required} h/wk · {s.status === 'GAP' ? `short ${s.required - got} h` : s.conditional ? 'covered, conditional' : s.status === 'MATCH' ? 'covered' : s.status.replace('_', ' ').toLowerCase()}
              </li>
            )
          })}
        </ul>
      )}
      {unplaced.length > 0 && (
        <div className="chart-legend">
          Not plotted (location outside this chart or unknown): {unplaced.map(s => `${s.area} ${s.segments.reduce((a, b) => a + b.hours, 0)}/${s.required} h`).join(' · ')}
        </div>
      )}
    </figure>
  )
}

function xy([x, y]: [number, number]) {
  return { x, y }
}

function SvgFlag({ kind }: { kind: FlagKind }) {
  if (kind === 'alfa') return (<g><path d="M0 0H30L24 10.5L30 21H0Z" fill="#1d4f91" /><rect width="13" height="21" fill="#fbf7ee" /><path d="M0 0H30L24 10.5L30 21H0Z" fill="none" stroke="#0b1622" /></g>)
  if (kind === 'bravo') return <path d="M0 0H30L24 10.5L30 21H0Z" fill="#d13a26" stroke="#0b1622" />
  return (<g><rect width="30" height="21" fill="#1d4f91" /><rect y="4.2" width="30" height="12.6" fill="#fbf7ee" /><rect y="7.4" width="30" height="6.3" fill="#d13a26" /></g>)
}
