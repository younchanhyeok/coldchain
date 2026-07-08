import type { Position, TrackerStatus } from './tracker'

export type ShipmentStatus = 'READY' | 'IN_TRANSIT' | 'DELIVERED'

export interface ShipmentSummary {
  shipmentId: number
  trackerId: string
  productName: string
  originName: string | null
  destinationName: string | null
  shipmentStatus: ShipmentStatus
  // 트래커 재사용과 무관하게 항상 "현재" 트래커 상태 — DELIVERED 화물은 "완료" 뱃지를 우선 표시.
  trackerStatus: TrackerStatus | null
  thresholdTemp: number | null
  lastTemperature: number | null
  lastPosition: Position | null
  lastReportedAt: string | null
  createdAt: string
  inTransitAt: string | null
  deliveredAt: string | null
}

export interface ShipmentListResponse {
  content: ShipmentSummary[]
  page: number
  size: number
  totalElements: number
}
