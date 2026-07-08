import { AlertTriangle, ThermometerSun } from 'lucide-react'
import { getAlerts } from '../../api/alerts'
import { usePolling } from '../../hooks/usePolling'
import type { Alert } from '../../types/alert'
import { DashboardCard } from '../dashboard/DashboardCard'

interface CargoAlertHistoryProps {
  trackerId: string | null
}

const STATUS_LABEL: Record<Alert['status'], string> = {
  PENDING: '발송 중',
  SENT: '발송됨',
  FAILED: '발송 실패',
}

export function CargoAlertHistory({ trackerId }: CargoAlertHistoryProps) {
  const { data } = usePolling(
    () => (trackerId ? getAlerts({ trackerId, size: 10 }) : Promise.resolve(null)),
    30_000,
    [trackerId],
  )
  const alerts = data?.content ?? []

  return (
    <DashboardCard title="알림 전송 이력" meta={`${data?.totalElements ?? 0}건`} bodyClassName="overflow-y-auto px-7 pb-7">
      {!trackerId ? (
        <p className="text-sm text-neutral-500">좌측에서 화물을 선택하세요.</p>
      ) : alerts.length === 0 ? (
        <p className="text-sm text-neutral-500">발송된 알림이 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {alerts.map((a) => {
            const Icon = a.type === 'BREACH' ? AlertTriangle : ThermometerSun
            const iconTone = a.type === 'BREACH' ? 'text-danger' : 'text-warning'
            return (
              <li key={a.id} className="flex items-start gap-3">
                <Icon size={16} className={`mt-0.5 shrink-0 ${iconTone}`} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2 text-sm text-neutral-200">
                    <span>{a.type === 'BREACH' ? '임계 이탈' : '이상 감지'}</span>
                    <span className="text-xs text-neutral-500">
                      {new Date(a.createdAt).toLocaleString('ko-KR')}
                    </span>
                  </div>
                  <div className="mt-0.5 truncate text-xs text-neutral-500" title={a.message}>
                    {a.message}
                  </div>
                  <div className="mt-1 text-xs text-neutral-600">
                    {STATUS_LABEL[a.status]}
                    {a.status === 'FAILED' && a.retryCount > 0 ? ` (재시도 ${a.retryCount}회)` : ''}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardCard>
  )
}
