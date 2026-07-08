export interface SummaryResponse {
  totalShipments: number
  inTransit: number
  breachCount: number
  deliveredCount: number
  // M3엔 예측이 없어 항상 0(생략이 아니라 정직한 0) — M4에서 값만 교체.
  rescuedByPrediction: number
  avgDeliveryMinutes: number | null
}
