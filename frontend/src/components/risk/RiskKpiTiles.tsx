import { Clock, Package, Sparkles, TriangleAlert } from 'lucide-react'
import type { SummaryResponse } from '../../types/summary'
import type { TrackerSummary } from '../../types/tracker'
import { KpiTile } from '../dashboard/KpiTile'

interface RiskKpiTilesProps {
  trackers: TrackerSummary[]
  summary: SummaryResponse | null
}

export function RiskKpiTiles({ trackers, summary }: RiskKpiTilesProps) {
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length
  const activePredictions = trackers.filter((t) => t.activePrediction != null).map((t) => t.activePrediction!)

  // predictedBreachAt 경과 후 EXPIRED 판정 전 과도 구간엔 leadTimeMinutes가 음수일 수 있어
  // (formatLeadTimeMinutes 참고) 평균이 왜곡되지 않도록 0으로 바닥을 둔다.
  const avgLeadTime =
    activePredictions.length > 0
      ? Math.round(
          activePredictions.reduce((sum, p) => sum + Math.max(0, p.leadTimeMinutes), 0) / activePredictions.length,
        )
      : null

  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <KpiTile
        icon={TriangleAlert}
        tone="danger"
        label="현재 위험"
        value={String(breachCount)}
        sub="임계 온도 초과 중"
        hasValue
      />
      <KpiTile
        icon={Sparkles}
        tone="warning"
        label="예측 위험"
        value={String(activePredictions.length)}
        sub="예측 경고 발효 중"
        hasValue
      />
      <KpiTile
        icon={Clock}
        tone="primary"
        label="평균 리드타임"
        value={avgLeadTime != null ? `${avgLeadTime}분` : '—'}
        sub="발효 중인 예측 기준"
        hasValue={avgLeadTime != null}
      />
      <KpiTile
        icon={Package}
        tone="success"
        label="구조된 박스"
        value={summary ? String(summary.rescuedByPrediction) : '—'}
        sub="예측 경고 후 이탈 없이 종료된 건수 포함"
        hasValue={summary != null}
      />
    </div>
  )
}
