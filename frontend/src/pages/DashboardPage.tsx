import { useEffect, useState } from 'react'
import { getTrackers } from '../api/trackers'
import type { StatusFilter } from '../components/layout/TopBar'
import { KpiTiles } from '../components/dashboard/KpiTiles'
import { RiskList } from '../components/dashboard/RiskList'
import { TemperatureChart } from '../components/dashboard/TemperatureChart'
import { usePolling } from '../hooks/usePolling'
import type { TrackerSummary } from '../types/tracker'

interface DashboardPageProps {
  statusFilter: StatusFilter
}

function pickDefaultTracker(trackers: TrackerSummary[]): string | null {
  if (trackers.length === 0) return null
  const mostOverThreshold = [...trackers].sort((a, b) => {
    const overA = a.lastTemperature == null ? -Infinity : a.lastTemperature - a.thresholdTemp
    const overB = b.lastTemperature == null ? -Infinity : b.lastTemperature - b.thresholdTemp
    return overB - overA
  })[0]
  return mostOverThreshold.trackerId
}

export function DashboardPage({ statusFilter }: DashboardPageProps) {
  const { data, error, loading } = usePolling(
    () => getTrackers({ status: statusFilter || undefined, size: 100 }),
    30_000,
    [statusFilter],
  )

  const trackers = data?.content ?? []
  const [selectedTrackerId, setSelectedTrackerId] = useState<string | null>(null)

  useEffect(() => {
    setSelectedTrackerId((current) => {
      if (current && trackers.some((t) => t.trackerId === current)) return current
      return pickDefaultTracker(trackers)
    })
  }, [trackers])

  if (loading && !data) {
    return <div className="text-slate-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-red-400">데이터를 불러오지 못했습니다: {error.message}</div>
  }

  return (
    <div className="flex flex-col gap-6">
      <KpiTiles trackers={trackers} />
      <RiskList trackers={trackers} selectedTrackerId={selectedTrackerId} onSelectTracker={setSelectedTrackerId} />
      <TemperatureChart
        trackers={trackers}
        selectedTrackerId={selectedTrackerId}
        onSelectTracker={setSelectedTrackerId}
      />
    </div>
  )
}
