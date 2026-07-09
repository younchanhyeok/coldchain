export type TrackerStatus = 'SAFE' | 'CAUTION' | 'RISK' | 'BREACH'

export interface Position {
  lat: number
  lon: number
}

export interface ActivePredictionSummary {
  predictedBreachAt: string
  // "지금부터 남은 분" — 화면의 "N분 후 이탈"에 그대로 쓴다.
  leadTimeMinutes: number
  slopePerMinute: number
}

export interface TrackerSummary {
  trackerId: string
  shipmentId: number | null
  productName: string
  originName: string | null
  destinationName: string | null
  thresholdTemp: number
  status: TrackerStatus
  lastTemperature: number | null
  lastPosition: Position | null
  lastReportedAt: string | null
  activePrediction: ActivePredictionSummary | null
}

export interface TrackerListResponse {
  content: TrackerSummary[]
  page: number
  size: number
  totalElements: number
}

export interface ShipmentDetail {
  origin: Position | null
  destination: Position | null
  consigneeName: string | null
  driverContact: string | null
  status: 'READY' | 'IN_TRANSIT' | 'DELIVERED'
}

// 백엔드가 @JsonUnwrapped로 TrackerSummaryResponse 필드를 최상위에 그대로 풀어 내려준다.
export interface TrackerDetail extends TrackerSummary {
  shipment: ShipmentDetail | null
  activeAnomalies: unknown[]
}
