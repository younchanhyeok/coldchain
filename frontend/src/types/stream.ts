import type { AlertSeverity, AlertStatus, AlertType } from './alert'
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
