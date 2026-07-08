import { Package, TrendingUp, TriangleAlert, Truck } from 'lucide-react'
import type { TrackerSummary } from '../../types/tracker'
import { KpiTile } from './KpiTile'

interface KpiTilesProps {
  trackers: TrackerSummary[]
}

export function KpiTiles({ trackers }: KpiTilesProps) {
  const inTransitCount = trackers.length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <KpiTile
        icon={Truck}
        tone="primary"
        label="배송 중"
        value={String(inTransitCount)}
        sub="조회된 트래커 수"
        hasValue
      />
      <KpiTile
        icon={TriangleAlert}
        tone="warning"
        label="이탈 위험"
        value={String(breachCount)}
        sub="임계 온도 초과"
        hasValue
      />
      <KpiTile icon={Package} tone="success" label="구조된 박스" value="—" sub="M4 예측 이후 제공" hasValue={false} />
      <KpiTile icon={TrendingUp} tone="danger" label="이탈률" value="—" sub="M4 예측 이후 제공" hasValue={false} />
    </div>
  )
}
