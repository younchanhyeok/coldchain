import type { TrackerSummary } from '../../types/tracker'

interface KpiTilesProps {
  trackers: TrackerSummary[]
}

export function KpiTiles({ trackers }: KpiTilesProps) {
  const inTransitCount = trackers.length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      <Tile label="배송 중" value={String(inTransitCount)} sub="조회된 트래커 수" tone="blue" />
      <Tile label="이탈 위험" value={String(breachCount)} sub="임계 온도 초과" tone="red" />
      <Tile label="구조된 박스" value="—" sub="M4 예측 이후 제공" tone="slate" />
      <Tile label="이탈률" value="—" sub="M4 예측 이후 제공" tone="slate" />
    </div>
  )
}

function Tile({
  label,
  value,
  sub,
  tone,
}: {
  label: string
  value: string
  sub: string
  tone: 'blue' | 'red' | 'slate'
}) {
  const toneClass = { blue: 'text-blue-400', red: 'text-red-400', slate: 'text-slate-600' }[tone]

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="text-sm text-slate-400">{label}</div>
      <div className={`mt-1 text-3xl font-semibold ${toneClass}`}>{value}</div>
      <div className="mt-1 text-xs text-slate-500">{sub}</div>
    </div>
  )
}
