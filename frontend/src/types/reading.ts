export interface ReadingPoint {
  ts: string
  temperature: number
  // M6 다운샘플(interval=1m|5m) 응답에서만 채워짐 — 원시 조회는 null.
  // 콜드체인에선 평균(temperature)이 짧은 이탈을 가릴 수 있어 maxTemperature가 이탈 안전 신호.
  minTemperature: number | null
  maxTemperature: number | null
  lat: number | null
  lon: number | null
}

export interface ReadingSeriesResponse {
  trackerId: string
  readings: ReadingPoint[]
  nextBefore: string | null
}
