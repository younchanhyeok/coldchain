import { CheckCircle2, Clock, Truck, TriangleAlert } from 'lucide-react'
import type { SummaryResponse } from '../../types/summary'
import { KpiTile } from '../dashboard/KpiTile'

interface CargoKpiTilesProps {
  summary: SummaryResponse | null
}

export function CargoKpiTiles({ summary }: CargoKpiTilesProps) {
  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <KpiTile
        icon={Truck}
        tone="primary"
        label="배송 중"
        value={summary ? String(summary.inTransit) : '—'}
        sub="진행 중인 배송"
        hasValue={summary != null}
      />
      <KpiTile
        icon={TriangleAlert}
        tone="warning"
        label="이탈 위험"
        value={summary ? String(summary.breachCount) : '—'}
        sub="임계 온도 초과"
        hasValue={summary != null}
      />
      <KpiTile
        icon={CheckCircle2}
        tone="success"
        label="배송 완료"
        value={summary ? String(summary.deliveredCount) : '—'}
        sub="누적 완료 건수"
        hasValue={summary != null}
      />
      <KpiTile
        icon={Clock}
        tone="danger"
        label="평균 배송시간"
        value={summary?.avgDeliveryMinutes != null ? `${summary.avgDeliveryMinutes}분` : '—'}
        sub={summary?.avgDeliveryMinutes != null ? '완료 건 기준' : '완료 건 없음'}
        hasValue={summary?.avgDeliveryMinutes != null}
      />
    </div>
  )
}
