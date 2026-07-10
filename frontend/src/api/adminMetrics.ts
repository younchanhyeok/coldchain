import { apiGet } from './client'
import type { AdminOverviewResponse } from '../types/adminOverview'
import type { PredictionMetricsResponse } from '../types/adminMetrics'

// 어드민 전용 엔드포인트라 X-Admin-Key가 필요하다(백엔드 기본값은 미설정=항상 401).
// JWT 개념이 없는 별도 인증 축이라 skipAuth로 Authorization 자동 주입·refresh 재시도를 건너뛴다.
// 키 자체는 Slack 웹훅과 같은 이유로 파일에 영구 저장하지 않고 환경변수로만 받는다.
const ADMIN_KEY = import.meta.env.VITE_ADMIN_KEY as string | undefined

export const hasAdminKey = ADMIN_KEY != null && ADMIN_KEY !== ''

export function getPredictionMetrics(params: {
  from: string
  to: string
  modelVersion?: string
}): Promise<PredictionMetricsResponse> {
  return apiGet<PredictionMetricsResponse>('/api/v1/admin/metrics/prediction', params, {
    skipAuth: true,
    headers: ADMIN_KEY ? { 'X-Admin-Key': ADMIN_KEY } : {},
  })
}

export function getAdminOverview(): Promise<AdminOverviewResponse> {
  return apiGet<AdminOverviewResponse>('/api/v1/admin/overview', undefined, {
    skipAuth: true,
    headers: ADMIN_KEY ? { 'X-Admin-Key': ADMIN_KEY } : {},
  })
}
