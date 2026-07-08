import { apiGet } from './client'
import type { AlertListResponse } from '../types/alert'

export function getAlerts(params: { trackerId?: string; page?: number; size?: number } = {}): Promise<AlertListResponse> {
  return apiGet<AlertListResponse>('/api/v1/alerts', params)
}
