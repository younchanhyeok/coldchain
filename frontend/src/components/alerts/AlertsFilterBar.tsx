export type AlertTypeFilter = '' | 'BREACH' | 'ANOMALY'
export type AlertPeriodFilter = 'today' | 'all'

interface AlertsFilterBarProps {
  typeFilter: AlertTypeFilter
  onTypeFilterChange: (value: AlertTypeFilter) => void
  periodFilter: AlertPeriodFilter
  onPeriodFilterChange: (value: AlertPeriodFilter) => void
  newAlertCount: number
  onDismissLiveBadge: () => void
}

// 채널 필터는 넣지 않음 — M3엔 SLACK 하나뿐이라 값이 하나인 드롭다운은 의미가 없다.
export function AlertsFilterBar({
  typeFilter,
  onTypeFilterChange,
  periodFilter,
  onPeriodFilterChange,
  newAlertCount,
  onDismissLiveBadge,
}: AlertsFilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      {newAlertCount > 0 && (
        <button
          type="button"
          onClick={onDismissLiveBadge}
          className="flex items-center gap-2 rounded-full bg-primary/15 px-3 py-1.5 text-xs font-medium text-primary"
        >
          <span className="h-1.5 w-1.5 rounded-full bg-primary" />
          새 알림 {newAlertCount}건
        </button>
      )}
      <div className="ml-auto flex items-center gap-3">
        <select
          className="h-11 rounded-control border border-border bg-card px-3 text-sm text-neutral-200"
          value={typeFilter}
          onChange={(e) => onTypeFilterChange(e.target.value as AlertTypeFilter)}
        >
          <option value="">전체 유형</option>
          <option value="BREACH">임계 이탈</option>
          <option value="ANOMALY">이상 감지</option>
        </select>
        <select
          className="h-11 rounded-control border border-border bg-card px-3 text-sm text-neutral-200"
          value={periodFilter}
          onChange={(e) => onPeriodFilterChange(e.target.value as AlertPeriodFilter)}
        >
          <option value="today">오늘</option>
          <option value="all">전체 기간</option>
        </select>
      </div>
    </div>
  )
}
