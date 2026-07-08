import { apiGet } from './client'
import type { SummaryResponse } from '../types/summary'

export function getSummary(): Promise<SummaryResponse> {
  return apiGet<SummaryResponse>('/api/v1/summary')
}
