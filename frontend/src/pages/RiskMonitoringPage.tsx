import { useEffect, useMemo, useState } from 'react'
import { getAlerts } from '../api/alerts'
import { getSummary } from '../api/summary'
import { TemperatureChart } from '../components/dashboard/TemperatureChart'
import { TrackerMap } from '../components/dashboard/TrackerMap'
import { RiskAlertPanel } from '../components/risk/RiskAlertPanel'
import { RiskKpiTiles } from '../components/risk/RiskKpiTiles'
import { RiskLiveBadge } from '../components/risk/RiskLiveBadge'
import { RiskPriorityList } from '../components/risk/RiskPriorityList'
import { usePolling } from '../hooks/usePolling'
import { ALERT_TYPE_LABEL } from '../lib/alertPresentation'
import type { Alert } from '../types/alert'
import type { TrackerSummary } from '../types/tracker'

function pickDefaultTrackerId(trackers: TrackerSummary[]): string | null {
  if (trackers.length === 0) return null
  const mostUrgent = [...trackers]
    .filter((t) => t.activePrediction != null)
    .sort((a, b) => new Date(a.activePrediction!.predictedBreachAt).getTime() - new Date(b.activePrediction!.predictedBreachAt).getTime())[0]
  return (mostUrgent ?? trackers[0]).trackerId
}

function describeAction(alert: Alert): string {
  const statusText = alert.status === 'SENT' ? 'Slack 발송됨' : alert.status === 'FAILED' ? 'Slack 발송 실패' : 'Slack 발송 대기 중'
  return `${ALERT_TYPE_LABEL[alert.type]} · ${statusText}`
}

interface RiskMonitoringPageProps {
  trackers: TrackerSummary[]
  loading: boolean
  error: Error | null
  lastPredictionAt: Date | null
}

export function RiskMonitoringPage({ trackers, loading, error, lastPredictionAt }: RiskMonitoringPageProps) {
  const [selectedTrackerId, setSelectedTrackerId] = useState<string | null>(null)
  const { data: summary } = usePolling(() => getSummary(), 30_000)
  const { data: alertList } = usePolling(() => getAlerts({ size: 30 }), 30_000)
  const recentAlerts = useMemo(() => alertList?.content ?? [], [alertList])
  // "최근 이벤트"(뭘 감지했나)와 달리 "수행된 조치"는 완료된 행동만 — PENDING(발송 시도 중)은
  // 아직 "한 일"이 아니므로 뺀다. 같은 이력을 라벨만 바꿔 두 번 보여주는 느낌을 줄인다.
  const completedActions = useMemo(() => recentAlerts.filter((a) => a.status !== 'PENDING'), [recentAlerts])

  useEffect(() => {
    setSelectedTrackerId((current) => {
      if (current && trackers.some((t) => t.trackerId === current)) return current
      return pickDefaultTrackerId(trackers)
    })
  }, [trackers])

  if (loading && trackers.length === 0) {
    return <div className="text-neutral-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-danger">데이터를 불러오지 못했습니다: {error.message}</div>
  }

  const selectedTracker = trackers.find((t) => t.trackerId === selectedTrackerId) ?? null

  return (
    <div className="flex flex-col gap-6">
      <RiskLiveBadge lastPredictionAt={lastPredictionAt} />
      <RiskKpiTiles trackers={trackers} summary={summary} />
      <div className="grid h-[480px] grid-cols-1 items-stretch gap-6 lg:grid-cols-[2fr_1fr]">
        <RiskPriorityList trackers={trackers} selectedTrackerId={selectedTrackerId} onSelectTracker={setSelectedTrackerId} />
        <TrackerMap
          trackerId={selectedTrackerId}
          currentPosition={selectedTracker?.lastPosition ?? null}
          trackers={trackers}
        />
      </div>
      <TemperatureChart trackers={trackers} selectedTrackerId={selectedTrackerId} onSelectTracker={setSelectedTrackerId} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <RiskAlertPanel
          title="최근 이벤트"
          alerts={recentAlerts}
          describe={(a) => ALERT_TYPE_LABEL[a.type]}
          emptyMessage="최근 이벤트가 없습니다."
        />
        <RiskAlertPanel
          title="수행된 조치"
          alerts={completedActions}
          describe={describeAction}
          emptyMessage="수행된 조치가 없습니다."
        />
      </div>
    </div>
  )
}
