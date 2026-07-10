export interface ConsigneeShipmentSummary {
  productName: string
  shipperName: string
  status: 'READY' | 'IN_TRANSIT' | 'DELIVERED'
  eta: string | null
}

export interface ConsigneeTemperatureLogPoint {
  ts: string
  temperature: number
}

export interface ConsigneeTrackResponse {
  shipment: ConsigneeShipmentSummary
  currentTemperature: number | null
  temperatureStatus: 'SAFE' | 'BREACH' | 'UNKNOWN'
  thresholdTemp: number
  position: { lat: number; lon: number } | null
  remainingDistanceMeters: number | null
  temperatureLog: ConsigneeTemperatureLogPoint[]
}
