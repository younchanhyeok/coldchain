import { apiGet } from './client'
import type { PredictionMetricsResponse } from '../types/adminMetrics'

// 어드민 전용 엔드포인트라 X-Admin-Key가 필요하다(백엔드 기본값은 미설정=항상 401).
// 이 프론트는 아직 인증 자체가 없으므로(RBAC은 M5) 로컬 개발 편의를 위해 환경변수로만 받는다
// — Slack 웹훅과 같은 이유로 파일에 영구 저장하지 않는다.
const ADMIN_KEY = import.meta.env.VITE_ADMIN_KEY as string | undefined

export const hasAdminKey = ADMIN_KEY != null && ADMIN_KEY !== ''

export function getPredictionMetrics(params: {
  from: string
  to: string
  modelVersion?: string
}): Promise<PredictionMetricsResponse> {
  return apiGet<PredictionMetricsResponse>(
    '/api/v1/admin/metrics/prediction',
    params,
    ADMIN_KEY ? { 'X-Admin-Key': ADMIN_KEY } : undefined,
  )
}
