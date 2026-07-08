import { haversineMeters } from '../../lib/geo'
import type { TrackResponse } from '../../types/track'
import type { TrackerDetail } from '../../types/tracker'
import { DashboardCard } from '../dashboard/DashboardCard'

interface LiveOpsProgressTimelineProps {
  tracker: TrackerDetail
  track: TrackResponse | null
}

// 진행률 = (총거리 - 남은거리) / 총거리, 총거리·남은거리 모두 직선거리 근사(ST_Distance/haversine).
// 도로 경로가 아니라는 점은 ETA와 동일한 한계 — 라벨도 "도착 예상"으로 통일한다(예측 아님).
export function LiveOpsProgressTimeline({ tracker, track }: LiveOpsProgressTimelineProps) {
  const origin = tracker.shipment?.origin
  const destination = tracker.shipment?.destination

  const progress = (() => {
    if (!origin || !destination || !track?.remainingDistanceMeters) return null
    const totalMeters = haversineMeters(origin, destination)
    if (totalMeters <= 0) return null
    const ratio = 1 - track.remainingDistanceMeters / totalMeters
    return Math.min(1, Math.max(0, ratio))
  })()

  return (
    <DashboardCard title="배송 진행">
      {progress == null ? (
        <p className="text-sm text-neutral-500">진행률을 계산할 데이터가 부족합니다.</p>
      ) : (
        <div>
          <div className="relative h-2 rounded-full bg-neutral-800">
            <div className="absolute inset-y-0 left-0 rounded-full bg-primary" style={{ width: `${progress * 100}%` }} />
            <div
              className="absolute top-1/2 h-3 w-3 -translate-y-1/2 rounded-full border-2 border-white bg-primary"
              style={{ left: `calc(${progress * 100}% - 6px)` }}
            />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs text-neutral-500">
            <span>출발</span>
            <span className="text-neutral-300">
              {Math.round(progress * 100)}%
              {track?.etaMinutes != null && ` · 도착 예상 ${Math.round(track.etaMinutes)}분 후`}
            </span>
            <span>도착</span>
          </div>
        </div>
      )}
    </DashboardCard>
  )
}
