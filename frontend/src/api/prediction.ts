import { apiGet } from './client'
import type { PredictionResponse } from '../types/prediction'

export function getPrediction(trackerId: string): Promise<PredictionResponse> {
  return apiGet<PredictionResponse>(`/api/v1/trackers/${trackerId}/prediction`)
}
