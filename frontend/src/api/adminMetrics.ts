import { apiGet } from './client'
import type { AdminOverviewResponse } from '../types/adminOverview'
import type { EvaluationRun, PredictionMetricsResponse } from '../types/adminMetrics'

// 어드민 전용 엔드포인트라 X-Admin-Key가 필요하다(백엔드 기본값은 미설정=항상 401).
// JWT 개념이 없는 별도 인증 축이라 skipAuth로 Authorization 자동 주입·refresh 재시도를 건너뛴다.
//
// 주의(리뷰 반영): Vite의 VITE_* 환경변수는 런타임 secret이 아니라 빌드 시점에 JS 번들 문자열로
// 그대로 새겨진다 — 이 프론트를 공개 배포하면 번들을 연 누구나 어드민 키를 읽을 수 있다.
// 로컬 docker-compose 데모(D3)에서만 쓰는 게 전제이며, 공개 배포는 서버사이드 프록시나
// 별도 어드민 로그인이 필요해 v2로 이연한다(README에도 명시 예정).
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

/** 최신순 평가 런 목록 — 리포트 탭의 런 셀렉터와 v1/v2 비교 카드가 쓴다. */
export function getEvaluationRuns(limit = 20): Promise<EvaluationRun[]> {
  return apiGet<EvaluationRun[]>('/api/v1/admin/evaluation-runs', { limit }, {
    skipAuth: true,
    headers: ADMIN_KEY ? { 'X-Admin-Key': ADMIN_KEY } : {},
  })
}
