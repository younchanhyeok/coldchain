export interface ReadingPoint {
  ts: string
  temperature: number
  lat: number | null
  lon: number | null
}

export interface ReadingSeriesResponse {
  trackerId: string
  readings: ReadingPoint[]
  nextBefore: string | null
}
