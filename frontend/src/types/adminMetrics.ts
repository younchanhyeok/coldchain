export interface PredictionEpisodeSummary {
  trackerId: string
  productName: string
  status: 'ACTIVE' | 'CANCELED' | 'INVALIDATED' | 'EXPIRED' | 'BREACHED'
  leadTimeMinutes: number | null
  createdAt: string
}

export interface PredictionMetricsResponse {
  modelVersion: string | null
  period: { from: string; to: string }
  totalPredictions: number
  truePositives: number
  falsePositives: number
  missedBreaches: number
  falsePositiveRate: number
  hitRate: number
  avgLeadTimeMinutes: number | null
  medianLeadTimeMinutes: number | null
  /** BREACHED 한정 avg(|예측 이탈시각 − 실제 이탈시각|) — "얼마나 정확히 맞췄나" 축(M7). */
  avgBreachTimingErrorMinutes: number | null
  episodes: PredictionEpisodeSummary[]
}

/**
 * 평가 런(M7) — 예측 지표의 시점 스냅샷. GET /admin/evaluation-runs 응답 한 건.
 * 에피소드 목록은 포함하지 않는 집계만 담긴다(상세는 해당 기간·버전으로 metrics 재조회).
 */
export interface EvaluationRun {
  id: number
  label: string | null
  periodStart: string
  periodEnd: string
  modelVersion: string | null
  triggerType: 'SCHEDULED' | 'MANUAL'
  totalPredictions: number
  truePositives: number
  falsePositives: number
  missedBreaches: number
  hitRate: number
  falsePositiveRate: number
  avgLeadTimeMinutes: number | null
  medianLeadTimeMinutes: number | null
  avgBreachTimingErrorMinutes: number | null
  createdAt: string
}
