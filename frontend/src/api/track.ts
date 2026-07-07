import { apiGet } from './client'
import type { TrackResponse } from '../types/track'

export function getTrack(trackerId: string): Promise<TrackResponse> {
  return apiGet<TrackResponse>(`/api/v1/trackers/${trackerId}/track`)
}