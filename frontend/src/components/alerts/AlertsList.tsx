import { AlertTriangle, ThermometerSun } from 'lucide-react'
import type { Alert } from '../../types/alert'
import { DashboardCard } from '../dashboard/DashboardCard'

interface AlertsListProps {
  alerts: Alert[]
  selectedAlertId: number | null
  onSelectAlert: (id: number) => void
}

const STATUS_DOT: Record<Alert['status'], string> = {
  PENDING: 'bg-neutral-500',
  SENT: 'bg-success',
  FAILED: 'bg-danger',
}

export function AlertsList({ alerts, selectedAlertId, onSelectAlert }: AlertsListProps) {
  return (
    <DashboardCard title="알림 리스트" meta={`${alerts.length}건`} bodyClassName="overflow-y-auto px-4 pb-4">
      {alerts.length === 0 ? (
        <p className="px-3 py-6 text-sm text-neutral-500">조건에 맞는 알림이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {alerts.map((a) => {
            const Icon = a.type === 'BREACH' ? AlertTriangle : ThermometerSun
            const iconTone = a.type === 'BREACH' ? 'text-danger' : 'text-warning'
            return (
              <li key={a.id}>
                <button
                  type="button"
                  onClick={() => onSelectAlert(a.id)}
                  className={`flex w-full items-start gap-3 rounded-md border px-4 py-3 text-left transition-colors duration-150 ${
                    a.id === selectedAlertId
                      ? 'border-l-2 border-l-primary border-y-border border-r-border bg-card-hover'
                      : 'border-l-2 border-l-transparent border-y-border border-r-border hover:bg-table-row-hover'
                  }`}
                >
                  <Icon size={16} className={`mt-0.5 shrink-0 ${iconTone}`} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-medium text-neutral-100">{a.trackerId}</span>
                      <span className="text-xs text-neutral-500">{new Date(a.createdAt).toLocaleString('ko-KR')}</span>
                    </div>
                    <div className="mt-1 flex items-center gap-2 text-xs text-neutral-400">
                      <span className={`h-1.5 w-1.5 rounded-full ${STATUS_DOT[a.status]}`} />
                      {a.type === 'BREACH' ? '임계 이탈' : '이상 감지'}
                      {a.temperatureAtEvent != null ? ` · ${a.temperatureAtEvent.toFixed(1)}℃` : ''}
                    </div>
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardCard>
  )
}
