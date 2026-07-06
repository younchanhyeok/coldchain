import { useEffect, useState } from 'react'
import { KpiTiles } from '../components/dashboard/KpiTiles'
import { RiskList } from '../components/dashboard/RiskList'
import { TemperatureChart } from '../components/dashboard/TemperatureChart'
import type { TrackerSummary } from '../types/tracker'

interface DashboardPageProps {
  trackers: TrackerSummary[]
  loading: boolean
  error: Error | null
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

export function DashboardPage({ trackers, loading, error }: DashboardPageProps) {
  const [selectedTrackerId, setSelectedTrackerId] = useState<string | null>(null)

  useEffect(() => {
    setSelectedTrackerId((current) => {
      if (current && trackers.some((t) => t.trackerId === current)) return current
      return pickDefaultTracker(trackers)
    })
  }, [trackers])

  if (loading && trackers.length === 0) {
    return <div className="text-neutral-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-danger">데이터를 불러오지 못했습니다: {error.message}</div>
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
