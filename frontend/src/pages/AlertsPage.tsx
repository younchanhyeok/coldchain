import { useEffect, useMemo, useState } from 'react'
import { getAlerts } from '../api/alerts'
import { AlertDetailPanel } from '../components/alerts/AlertDetailPanel'
import { AlertsFilterBar, type AlertPeriodFilter, type AlertTypeFilter } from '../components/alerts/AlertsFilterBar'
import { AlertsKpiTiles } from '../components/alerts/AlertsKpiTiles'
import { AlertsList } from '../components/alerts/AlertsList'
import { useAlertLiveBadge } from '../hooks/useAlertLiveBadge'
import { usePolling } from '../hooks/usePolling'
import type { Alert } from '../types/alert'

function isToday(iso: string): boolean {
  return new Date(iso).toDateString() === new Date().toDateString()
}

function matchesFilters(alert: Alert, typeFilter: AlertTypeFilter, periodFilter: AlertPeriodFilter): boolean {
  if (typeFilter && alert.type !== typeFilter) return false
  if (periodFilter === 'today' && !isToday(alert.createdAt)) return false
  return true
}

export function AlertsPage() {
  const { data: alertList, loading, error } = usePolling(() => getAlerts({ size: 200 }), 30_000)
  const { newAlertCount, reset: dismissLiveBadge } = useAlertLiveBadge()

  const [typeFilter, setTypeFilter] = useState<AlertTypeFilter>('')
  const [periodFilter, setPeriodFilter] = useState<AlertPeriodFilter>('today')
  const [selectedAlertId, setSelectedAlertId] = useState<number | null>(null)

  const alerts = useMemo(() => alertList?.content ?? [], [alertList])

  const filteredAlerts = useMemo(
    () => alerts.filter((a) => matchesFilters(a, typeFilter, periodFilter)),
    [alerts, typeFilter, periodFilter],
  )

  useEffect(() => {
    setSelectedAlertId((current) => {
      if (current != null && filteredAlerts.some((a) => a.id === current)) return current
      return filteredAlerts[0]?.id ?? null
    })
  }, [filteredAlerts])

  if (loading && alerts.length === 0) {
    return <div className="text-neutral-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-danger">데이터를 불러오지 못했습니다: {error.message}</div>
  }

  const selectedAlert = filteredAlerts.find((a) => a.id === selectedAlertId) ?? null

  return (
    <div className="flex flex-col gap-6">
      <AlertsKpiTiles alerts={alerts} />
      <AlertsFilterBar
        typeFilter={typeFilter}
        onTypeFilterChange={setTypeFilter}
        periodFilter={periodFilter}
        onPeriodFilterChange={setPeriodFilter}
        newAlertCount={newAlertCount}
        onDismissLiveBadge={dismissLiveBadge}
      />
      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="h-[750px]">
          <AlertsList alerts={filteredAlerts} selectedAlertId={selectedAlertId} onSelectAlert={setSelectedAlertId} />
        </div>
        <div className="sticky top-6">
          <AlertDetailPanel alert={selectedAlert} />
        </div>
      </div>
    </div>
  )
}
