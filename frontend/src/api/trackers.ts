import { apiGet } from './client'
import type { TrackerDetail, TrackerListResponse } from '../types/tracker'

export function getTrackers(
  params: { status?: string; shipmentStatus?: string; size?: number } = {},
): Promise<TrackerListResponse> {
  return apiGet<TrackerListResponse>('/api/v1/trackers', params)
}

export function getTrackerDetail(trackerId: string): Promise<TrackerDetail> {
  return apiGet<TrackerDetail>(`/api/v1/trackers/${trackerId}`)
}
