import type { ConsigneeTrackResponse } from '../types/consigneeTrack'
import { apiGet } from './client'

// 계정·로그인이 없는 매직링크 뷰 — 토큰이 곧 인가 스코프라 JWT 개념 자체가 없다(skipAuth).
export function getConsigneeTrack(token: string): Promise<ConsigneeTrackResponse> {
  return apiGet<ConsigneeTrackResponse>(`/api/v1/track/${token}`, undefined, { skipAuth: true })
}
