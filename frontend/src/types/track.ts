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
  // 직선거리/평균속도 기반 근사치 — 실제 도로 경로 ETA 아님, 정지 중이면 null.
  etaMinutes: number | null
  breachSegments: GeoJsonLineString[]
}
