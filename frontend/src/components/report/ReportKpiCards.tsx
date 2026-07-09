import { Clock, Package, Target, TriangleAlert } from 'lucide-react'
import type { PredictionMetricsResponse } from '../../types/adminMetrics'
import { KpiTile } from '../dashboard/KpiTile'

interface ReportKpiCardsProps {
  metrics: PredictionMetricsResponse | null
  /** GET /summary 누적치 — 평가 기간(admin metrics)과 무관한 전체 누적이라 별도로 받는다. */
  rescuedByPrediction: number | null
}

const formatPercent = (ratio: number) => `${Math.round(ratio * 100)}%`

export function ReportKpiCards({ metrics, rescuedByPrediction }: ReportKpiCardsProps) {
  const hasEvaluated = metrics != null && metrics.truePositives + metrics.falsePositives > 0

  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <KpiTile
        icon={Clock}
        tone="primary"
        label="평균 리드타임"
        value={metrics?.avgLeadTimeMinutes != null ? `${metrics.avgLeadTimeMinutes.toFixed(1)}분` : '—'}
        sub="최초 경고가 실제 이탈보다 앞선 시간"
        hasValue={metrics?.avgLeadTimeMinutes != null}
      />
      <KpiTile
        icon={TriangleAlert}
        tone="warning"
        label="오탐률"
        value={hasEvaluated ? formatPercent(metrics.falsePositiveRate) : '—'}
        sub="종결된 경고 중 미이탈 비율(FP/(TP+FP))"
        hasValue={hasEvaluated}
      />
      <KpiTile
        icon={Target}
        tone="success"
        label="적중률"
        value={hasEvaluated ? formatPercent(metrics.hitRate) : '—'}
        sub="종결된 경고 중 실제 이탈 비율(precision)"
        hasValue={hasEvaluated}
      />
      <KpiTile
        icon={Package}
        tone="success"
        label="구조된 박스"
        value={rescuedByPrediction != null ? String(rescuedByPrediction) : '—'}
        sub="예측 경고 후 이탈 없이 종료된 건수 포함(누적)"
        hasValue={rescuedByPrediction != null}
      />
    </div>
  )
}
