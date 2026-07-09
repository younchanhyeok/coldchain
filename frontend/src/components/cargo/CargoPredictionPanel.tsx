import { Sparkles } from 'lucide-react'
import { getPrediction } from '../../api/prediction'
import { usePolling } from '../../hooks/usePolling'
import type { PredictionStatus } from '../../types/prediction'
import { DashboardCard } from '../dashboard/DashboardCard'

interface CargoPredictionPanelProps {
  trackerId: string
}

const STATUS_BADGE: Record<PredictionStatus, { label: string; className: string }> = {
  NONE: { label: '신호 없음', className: 'bg-neutral-700/40 text-neutral-300' },
  ACTIVE: { label: '위험 감지', className: 'bg-warning/15 text-warning' },
  CANCELED: { label: '해제됨', className: 'bg-primary/15 text-primary' },
  INVALIDATED: { label: '무효화', className: 'bg-neutral-700/40 text-neutral-300' },
  EXPIRED: { label: '만료', className: 'bg-neutral-700/40 text-neutral-300' },
  BREACHED: { label: '적중', className: 'bg-danger/15 text-danger' },
}

// 급변(SUDDEN) 감지 시 예측을 버리고 즉시 알림으로 전환하는 안전한 실패 설계를 그대로 문구로
// 드러낸다 — INVALIDATED를 다른 상태처럼 얼버무리지 않는다(M4 방어 원칙: 한계를 먼저 말한다).
const STATUS_MESSAGE: Record<PredictionStatus, string> = {
  NONE: '현재 예측 경고 없음',
  ACTIVE: '추세 기반 선제 경고 중',
  CANCELED: '추세 완화로 예측 해제됨',
  INVALIDATED: '급변 감지로 예측 무효화 — 즉시 알림으로 전환됨',
  EXPIRED: '예상 시각 경과, 이탈 없이 종료',
  BREACHED: '예측대로 이탈 발생',
}

export function CargoPredictionPanel({ trackerId }: CargoPredictionPanelProps) {
  const { data: prediction } = usePolling(() => getPrediction(trackerId), 30_000, [trackerId])

  const status = prediction?.status ?? 'NONE'
  const badge = STATUS_BADGE[status]

  return (
    <DashboardCard
      title="AI 예측"
      status={
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${badge.className}`}>{badge.label}</span>
      }
    >
      <div className="flex items-start gap-3">
        <Sparkles size={18} className="mt-0.5 shrink-0 text-neutral-600" />
        <div className="flex flex-col gap-1 text-sm">
          <span className="text-neutral-300">{STATUS_MESSAGE[status]}</span>

          {status === 'ACTIVE' && prediction && (
            <>
              {prediction.leadTimeMinutes != null && (
                <span className="text-neutral-200">약 {Math.round(prediction.leadTimeMinutes)}분 후 임계 도달 예상</span>
              )}
              {prediction.predictedBreachAt && (
                <span className="text-xs text-neutral-500">
                  예상 시각 {new Date(prediction.predictedBreachAt).toLocaleString('ko-KR')}
                </span>
              )}
              {prediction.slopePerMinute != null && (
                <span className="text-xs text-neutral-500">기울기 {prediction.slopePerMinute.toFixed(2)}℃/분</span>
              )}
            </>
          )}

          {prediction?.modelVersion && <span className="text-xs text-neutral-600">{prediction.modelVersion} 기준</span>}
        </div>
      </div>
    </DashboardCard>
  )
}
