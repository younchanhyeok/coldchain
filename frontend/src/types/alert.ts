export type AlertType = 'BREACH' | 'ANOMALY'
export type AlertSeverity = 'HIGH' | 'MEDIUM'
export type AlertChannel = 'SLACK'
export type AlertStatus = 'PENDING' | 'SENT' | 'FAILED'

export interface Alert {
  id: number
  trackerId: string
  type: AlertType
  severity: AlertSeverity
  temperatureAtEvent: number | null
  // 실제 Slack에 발송된 payload 원문 그대로.
  message: string
  channel: AlertChannel
  status: AlertStatus
  retryCount: number
  createdAt: string
}

export interface AlertListResponse {
  content: Alert[]
  page: number
  size: number
  totalElements: number
}
