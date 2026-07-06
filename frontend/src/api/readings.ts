import { apiGet } from './client'
import type { ReadingSeriesResponse } from '../types/reading'

export function getReadings(trackerId: string, from: string, to: string): Promise<ReadingSeriesResponse> {
  return apiGet<ReadingSeriesResponse>(`/api/v1/trackers/${trackerId}/readings`, { from, to })
}
