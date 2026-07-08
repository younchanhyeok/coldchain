import { useEffect, useMemo, useState } from 'react'
import { getAlerts } from '../api/alerts'
import { getShipments } from '../api/shipments'
import { getTrack } from '../api/track'
import { getTrackerDetail } from '../api/trackers'
import { CargoDetailPanel } from '../components/cargo/CargoDetailPanel'
import { LiveOpsKpiTiles } from '../components/liveops/LiveOpsKpiTiles'
import { LiveOpsMap } from '../components/liveops/LiveOpsMap'
import { LiveOpsProgressTimeline } from '../components/liveops/LiveOpsProgressTimeline'
import { LiveOpsRecentEvents } from '../components/liveops/LiveOpsRecentEvents'
import { usePolling } from '../hooks/usePolling'
import type { TrackerSummary } from '../types/tracker'

const MAX_TRACKERS_FOR_AVG_ETA = 20

function pickDefaultTrackerId(trackers: TrackerSummary[]): string | null {
  if (trackers.length === 0) return null
  const breached = trackers.find((t) => t.status === 'BREACH')
  return (breached ?? trackers[0]).trackerId
}

interface LiveOpsMapPageProps {
  trackers: TrackerSummary[]
  loading: boolean
  error: Error | null
}

export function LiveOpsMapPage({ trackers, loading, error }: LiveOpsMapPageProps) {
  const [keyword, setKeyword] = useState('')
  const [selectedTrackerId, setSelectedTrackerId] = useState<string | null>(null)
  const [avgEtaMinutes, setAvgEtaMinutes] = useState<number | null>(null)

  const filteredTrackers = useMemo(
    () =>
      keyword.trim() === ''
        ? trackers
        : trackers.filter((t) => t.trackerId.toLowerCase().includes(keyword.trim().toLowerCase())),
    [trackers, keyword],
  )

  useEffect(() => {
    setSelectedTrackerId((current) => {
      if (current && filteredTrackers.some((t) => t.trackerId === current)) return current
      return pickDefaultTrackerId(filteredTrackers)
    })
  }, [filteredTrackers])

  // 평균 ETA는 화면에 보이는 트래커마다 개별 track 호출 후 평균을 낸다(집계 API 없음) —
  // 트래커 수가 많아지면 N+1로 부담이 커지므로 상한 넘으면 계산 자체를 생략(정직하게 "—").
  useEffect(() => {
    let cancelled = false
    if (trackers.length === 0 || trackers.length > MAX_TRACKERS_FOR_AVG_ETA) {
      setAvgEtaMinutes(null)
      return
    }
    Promise.all(trackers.map((t) => getTrack(t.trackerId).catch(() => null))).then((results) => {
      if (cancelled) return
      const values = results.filter((r): r is NonNullable<typeof r> => r?.etaMinutes != null).map((r) => r.etaMinutes!)
      setAvgEtaMinutes(values.length > 0 ? values.reduce((a, b) => a + b, 0) / values.length : null)
    })
    return () => {
      cancelled = true
    }
  }, [trackers])

  const { data: tracker } = usePolling(
    () => (selectedTrackerId ? getTrackerDetail(selectedTrackerId) : Promise.resolve(null)),
    30_000,
    [selectedTrackerId],
  )
  const { data: track } = usePolling(
    () => (selectedTrackerId ? getTrack(selectedTrackerId) : Promise.resolve(null)),
    30_000,
    [selectedTrackerId],
  )
  const { data: shipmentList } = usePolling(() => getShipments({ size: 200 }), 30_000)
  const { data: alertList } = usePolling(
    () => (selectedTrackerId ? getAlerts({ trackerId: selectedTrackerId, size: 10 }) : Promise.resolve(null)),
    30_000,
    [selectedTrackerId],
  )

  const selectedShipment = shipmentList?.content.find((s) => s.trackerId === selectedTrackerId) ?? null

  if (loading && trackers.length === 0) {
    return <div className="text-neutral-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-danger">데이터를 불러오지 못했습니다: {error.message}</div>
  }

  return (
    <div className="flex flex-col gap-6">
      <LiveOpsKpiTiles trackers={trackers} avgEtaMinutes={avgEtaMinutes} />
      <input
        type="text"
        placeholder="트래커 ID 검색"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        className="h-11 w-full max-w-xs rounded-control border border-border bg-card px-3 text-sm text-neutral-200 placeholder:text-neutral-600"
      />
      {/* 지도가 이 화면의 주인공 — 전체 폭 + 큰 높이로 상단에 두고, 상세/분석은 그 아래
          가로로 배치한다(지도 옆 좁은 사이드바에 카드 6개를 쌓지 않는다). */}
      <div className="h-[500px]">
        <LiveOpsMap
          trackers={filteredTrackers}
          selectedTrackerId={selectedTrackerId}
          onSelectTracker={setSelectedTrackerId}
        />
      </div>
      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[7fr_5fr]">
        <div className="flex flex-col gap-6">
          <CargoDetailPanel trackerId={selectedTrackerId} />
          {tracker && <LiveOpsProgressTimeline tracker={tracker} track={track} />}
        </div>
        <LiveOpsRecentEvents shipment={selectedShipment} alerts={alertList?.content ?? []} />
      </div>
    </div>
  )
}
