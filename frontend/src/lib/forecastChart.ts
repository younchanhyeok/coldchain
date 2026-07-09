import type { PredictionResponse } from '../types/prediction'

export interface ForecastChartPoint {
  ts: string
  temperature?: number
  forecastTemperature?: number
}

/**
 * 실측 실선 데이터에 예측 점선을 이어 붙인다 — 마지막 실측점을 실선/점선의 분기점으로 삼아
 * (temperature·forecastTemperature 둘 다 채움) 시각적으로 끊어지지 않게 한다. ACTIVE 상태에
 * forecast가 정확히 2점(anchor·임계 도달점)일 때만 적용 — 그 외 상태는 실측만 그대로 반환한다.
 */
export function withForecast(
  actual: ForecastChartPoint[],
  prediction: PredictionResponse | null,
  formatTs: (iso: string) => string,
): ForecastChartPoint[] {
  if (!prediction || prediction.status !== 'ACTIVE' || prediction.forecast.length !== 2 || actual.length === 0) {
    return actual
  }
  const [, breachPoint] = prediction.forecast
  const last = actual[actual.length - 1]
  return [
    ...actual.slice(0, -1),
    { ...last, forecastTemperature: last.temperature },
    { ts: formatTs(breachPoint.ts), forecastTemperature: breachPoint.temperature },
  ]
}
