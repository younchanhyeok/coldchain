import { AlertTriangle, Sparkles, ThermometerSun, type LucideIcon } from 'lucide-react'
import type { AlertType } from '../types/alert'

// 알림 유형별 라벨·아이콘·색조 — 알림 센터/화물관리/위험 모니터링 등 여러 화면이 공유한다.
// 유형이 늘 때(M4 PREDICTION 추가 등) 여기 한 곳만 고치면 전체 화면에 반영된다.
export const ALERT_TYPE_LABEL: Record<AlertType, string> = {
  BREACH: '임계 이탈',
  ANOMALY: '이상 감지',
  PREDICTION: '예측 경고',
  PREDICTION_CANCELED: '예측 해제',
}

export const ALERT_TYPE_ICON: Record<AlertType, LucideIcon> = {
  BREACH: AlertTriangle,
  ANOMALY: ThermometerSun,
  PREDICTION: Sparkles,
  PREDICTION_CANCELED: Sparkles,
}

export const ALERT_TYPE_TONE: Record<AlertType, string> = {
  BREACH: 'text-danger',
  ANOMALY: 'text-warning',
  PREDICTION: 'text-warning',
  PREDICTION_CANCELED: 'text-primary',
}
