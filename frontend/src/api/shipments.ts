import { apiGet } from './client'
import type { ShipmentListResponse } from '../types/shipment'

export function getShipments(params: { size?: number } = {}): Promise<ShipmentListResponse> {
  return apiGet<ShipmentListResponse>('/api/v1/shipments', params)
}
