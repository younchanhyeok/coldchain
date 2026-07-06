import { apiGet } from './client'
import type { TrackerListResponse } from '../types/tracker'

export function getTrackers(
  params: { status?: string; shipmentStatus?: string; size?: number } = {},
): Promise<TrackerListResponse> {
  return apiGet<TrackerListResponse>('/api/v1/trackers', params)
}
