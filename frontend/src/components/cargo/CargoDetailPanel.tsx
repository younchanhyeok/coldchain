import { getAlerts } from '../../api/alerts'
import { getTrack } from '../../api/track'
import { getTrackerDetail } from '../../api/trackers'
import { usePolling } from '../../hooks/usePolling'
import { CargoPredictionPlaceholder } from './CargoPredictionPlaceholder'
import { CargoShipmentInfo } from './CargoShipmentInfo'
import { CargoStatusOverview } from './CargoStatusOverview'
import { CargoTemperatureChart } from './CargoTemperatureChart'

interface CargoDetailPanelProps {
  trackerId: string | null
}

export function CargoDetailPanel({ trackerId }: CargoDetailPanelProps) {
  const { data: tracker } = usePolling(
    () => (trackerId ? getTrackerDetail(trackerId) : Promise.resolve(null)),
    30_000,
    [trackerId],
  )

  // etaMinutes는 진행 중 배송에만 의미가 있다 — 완료/배송없음이면 /track이 404라 폴링하지 않는다.
  const { data: track } = usePolling(
    () => (trackerId && tracker?.shipment?.status === 'IN_TRANSIT' ? getTrack(trackerId) : Promise.resolve(null)),
    30_000,
    [trackerId, tracker?.shipment?.status],
  )

  // 이탈 시작 시각 근사치 — BREACH 알림은 전이 시에만 발행되므로 가장 최근 BREACH 알림 시각이
  // 곧 이번 이탈이 시작된 시각과 같다(신규 백엔드 필드 없이 기존 alert 이력 재사용).
  const { data: recentAlerts } = usePolling(
    () => (trackerId ? getAlerts({ trackerId, size: 5 }) : Promise.resolve(null)),
    30_000,
    [trackerId],
  )
  const breachStartedAt = recentAlerts?.content.find((a) => a.type === 'BREACH')?.createdAt ?? null

  if (!trackerId) {
    return (
      <div className="flex h-full min-h-[400px] items-center justify-center rounded-card border border-border bg-card text-sm text-neutral-500 shadow-card">
        좌측에서 화물을 선택하세요.
      </div>
    )
  }

  if (!tracker) {
    return (
      <div className="flex h-full min-h-[400px] items-center justify-center rounded-card border border-border bg-card text-sm text-neutral-500 shadow-card">
        불러오는 중...
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <CargoStatusOverview tracker={tracker} etaMinutes={track?.etaMinutes ?? null} breachStartedAt={breachStartedAt} />
      <CargoShipmentInfo tracker={tracker} />
      <CargoTemperatureChart trackerId={trackerId} thresholdTemp={tracker.thresholdTemp} />
      <CargoPredictionPlaceholder />
    </div>
  )
}
