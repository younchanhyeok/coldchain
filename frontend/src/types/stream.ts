import type { AlertSeverity, AlertStatus, AlertType } from './alert'
import type { PredictionStatus } from './prediction'
import type { TrackerStatus } from './tracker'

export interface ReadingStreamEvent {
  trackerId: string
  temperature: number
  lat: number
  lon: number
  ts: string
  status: TrackerStatus
}

export interface AlertStreamEvent {
  id: number
  trackerId: string
  type: AlertType
  severity: AlertSeverity
  status: AlertStatus
  createdAt: string
}

export interface PredictionStreamEvent {
  trackerId: string
  status: PredictionStatus
  predictedBreachAt: string | null
  slopePerMinute: number | null
  modelVersion: string | null
  createdAt: string | null
}
