export interface PredictionEpisodeSummary {
  trackerId: string
  productName: string
  status: 'ACTIVE' | 'CANCELED' | 'INVALIDATED' | 'EXPIRED' | 'BREACHED'
  leadTimeMinutes: number | null
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
  episodes: PredictionEpisodeSummary[]
}
