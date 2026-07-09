export type PredictionStatus = 'NONE' | 'ACTIVE' | 'CANCELED' | 'INVALIDATED' | 'EXPIRED' | 'BREACHED'

export interface ForecastPoint {
  ts: string
  temperature: number
}

export interface PredictionResponse {
  status: PredictionStatus
  predictedBreachAt: string | null
  // "지금부터 남은 분" — status가 ACTIVE일 때만 값이 있다.
  leadTimeMinutes: number | null
  thresholdTemp: number | null
  slopePerMinute: number | null
  modelVersion: string | null
  createdAt: string | null
  // ACTIVE일 때만 2점(anchor 실측점→임계 도달점) — 나머지 상태는 빈 배열.
  forecast: ForecastPoint[]
}
