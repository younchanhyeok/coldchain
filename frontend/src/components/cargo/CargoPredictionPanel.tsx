import { Sparkles } from 'lucide-react'
import { getPrediction } from '../../api/prediction'
import { usePolling } from '../../hooks/usePolling'
import { usePredictionRefreshSignal } from '../../hooks/usePredictionRefreshSignal'
import type { PredictionResponse, PredictionStatus } from '../../types/prediction'
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

const formatTime = (iso: string) => new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })

const formatSlope = (slope: number) => `${slope > 0 ? '+' : ''}${slope.toFixed(2)}`

// 신뢰도%·위험도% 같은 창작 수치는 절대 넣지 않는다(M4 거부 목록). 대신 이미 응답에 오는 실측
// 필드(createdAt·slopePerMinute·predictedBreachAt·thresholdTemp)만으로 "언제·왜 경고했고
// 어떻게 됐는지"를 자연어 문장으로 구성한다 — 선형회귀를 쓴 이유가 "기울기가 곧 근거"였는데
// 배지만 보여주면 그 설명 가능성이 묻힌다.
function buildMessage(prediction: PredictionResponse | null): string {
  if (!prediction || prediction.status === 'NONE') return '현재 추세 안정 — 예측 경고 없음'

  const { status, createdAt, slopePerMinute, predictedBreachAt, thresholdTemp, leadTimeMinutes } = prediction
  const warnedAt = createdAt ? formatTime(createdAt) : null
  const slopeText = slopePerMinute != null ? `분당 ${formatSlope(slopePerMinute)}℃` : null

  switch (status) {
    case 'ACTIVE': {
      const trend = slopeText ? `최근 온도가 ${slopeText}로 오르는 중` : '온도 상승 추세 감지'
      if (leadTimeMinutes == null || thresholdTemp == null || !predictedBreachAt) return trend
      return `${trend} → 이 추세면 약 ${Math.round(leadTimeMinutes)}분 후 ${thresholdTemp.toFixed(1)}℃ 초과 예상 (${formatTime(predictedBreachAt)})`
    }
    case 'CANCELED':
      if (!warnedAt || !slopeText) return '추세 완화로 예측 해제됨'
      return `${warnedAt}에 ${slopeText} 상승 추세로 경고했으나, 이후 온도가 안정되어(연속 3회 완화) 해제됨`
    case 'BREACHED':
      return warnedAt ? `예측대로 이탈 발생. 최초 경고(${warnedAt})보다 앞서 감지` : '예측대로 이탈 발생'
    case 'EXPIRED':
      return predictedBreachAt
        ? `예상 시각(${formatTime(predictedBreachAt)}) 경과, 이탈 없이 종료`
        : '예상 시각 경과, 이탈 없이 종료'
    case 'INVALIDATED':
      return warnedAt
        ? `급변 감지로 예측 무효화 — 즉시 알림으로 전환됨 (경고: ${warnedAt})`
        : '급변 감지로 예측 무효화 — 즉시 알림으로 전환됨'
    default:
      return ''
  }
}

export function CargoPredictionPanel({ trackerId }: CargoPredictionPanelProps) {
  const predictionSignal = usePredictionRefreshSignal(trackerId)
  const { data: prediction } = usePolling(() => getPrediction(trackerId), 30_000, [trackerId, predictionSignal])

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
          <span className="text-neutral-300">{buildMessage(prediction)}</span>
          {prediction?.modelVersion && <span className="text-xs text-neutral-600">{prediction.modelVersion} 기준</span>}
        </div>
      </div>
    </DashboardCard>
  )
}
