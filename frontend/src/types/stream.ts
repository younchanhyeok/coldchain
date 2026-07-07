import type { TrackerStatus } from './tracker'

export interface ReadingStreamEvent {
  trackerId: string
  temperature: number
  lat: number
  lon: number
  ts: string
  status: TrackerStatus
}
