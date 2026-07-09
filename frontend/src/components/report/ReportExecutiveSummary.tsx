import { Sparkles } from 'lucide-react'
import type { PredictionMetricsResponse } from '../../types/adminMetrics'
import { DashboardCard } from '../dashboard/DashboardCard'

interface ReportExecutiveSummaryProps {
  metrics: PredictionMetricsResponse | null
}

// 실측 수치만 문장으로 조립한다 — 금액·전월비 등 창작 비교는 넣지 않는다(화면_탭_구성.md).
// falsePositives는 CANCELED+EXPIRED와 같은 집합이라 "구조"·"오탐" 두 관점을 한 문장에 나란히
// 둬서 그 관점 차이를 숨기지 않는다(API_명세.md의 rescuedByPrediction 설명과 일관).
export function ReportExecutiveSummary({ metrics }: ReportExecutiveSummaryProps) {
  const sentence = (() => {
    if (!metrics || metrics.totalPredictions === 0) return '해당 기간에 발행된 예측 경고가 없습니다.'
    const leadTime = metrics.avgLeadTimeMinutes != null ? `${metrics.avgLeadTimeMinutes.toFixed(1)}분` : '측정 불가'
    return `이번 평가 기간 예측 경고 ${metrics.totalPredictions}건 발행, 그중 실제 이탈 적중 ${metrics.truePositives}건 · 구조(오탐) ${metrics.falsePositives}건, 평균 리드타임 ${leadTime}.`
  })()

  return (
    <DashboardCard title="Executive Summary">
      <div className="flex items-start gap-3">
        <Sparkles size={18} className="mt-0.5 shrink-0 text-primary" />
        <p className="text-sm text-neutral-200">{sentence}</p>
      </div>
    </DashboardCard>
  )
}
