import { Clock, TriangleAlert, Truck } from 'lucide-react'
import type { TrackerSummary } from '../../types/tracker'
import { KpiTile } from '../dashboard/KpiTile'

interface LiveOpsKpiTilesProps {
  trackers: TrackerSummary[]
  avgEtaMinutes: number | null
}

// "운행 차량"은 배송 중과 동일 개념이라 통합(화면_탭_구성.md).
export function LiveOpsKpiTiles({ trackers, avgEtaMinutes }: LiveOpsKpiTilesProps) {
  const inTransitCount = trackers.length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
      <KpiTile icon={Truck} tone="primary" label="배송 중" value={String(inTransitCount)} sub="운행 차량 수" hasValue />
      <KpiTile
        icon={TriangleAlert}
        tone="warning"
        label="이탈"
        value={String(breachCount)}
        sub="임계 온도 초과"
        hasValue
      />
      <KpiTile
        icon={Clock}
        tone="danger"
        label="평균 ETA"
        value={avgEtaMinutes != null ? `${Math.round(avgEtaMinutes)}분` : '—'}
        sub={avgEtaMinutes != null ? '도착 예상 평균(근사치)' : '계산 불가'}
        hasValue={avgEtaMinutes != null}
      />
    </div>
  )
}
