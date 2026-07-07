export interface GeoJsonLineString {
  type: 'LineString'
  coordinates: [number, number][] // [lon, lat]
}

export interface NamedPosition {
  lat: number
  lon: number
  name: string
}

export interface TrackResponse {
  trackerId: string
  path: GeoJsonLineString
  current: { lat: number; lon: number } | null
  destination: NamedPosition
  remainingDistanceMeters: number | null
  breachSegments: GeoJsonLineString[]
}
