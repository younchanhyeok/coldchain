export type TrackerStatus = 'SAFE' | 'CAUTION' | 'RISK' | 'BREACH'

export interface Position {
  lat: number
  lon: number
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
  activePrediction: unknown | null
}

export interface TrackerListResponse {
  content: TrackerSummary[]
  page: number
  size: number
  totalElements: number
}
