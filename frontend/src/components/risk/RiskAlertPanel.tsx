import { ALERT_TYPE_ICON, ALERT_TYPE_TONE } from '../../lib/alertPresentation'
import type { Alert } from '../../types/alert'
import { DashboardCard } from '../dashboard/DashboardCard'

interface RiskAlertPanelProps {
  title: string
  alerts: Alert[]
  describe: (alert: Alert) => string
  emptyMessage: string
}

const formatTime = (iso: string) => new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })

// "최근 이벤트"(뭘 감지했나)와 "수행된 조치"(뭘 했나)가 같은 alert 이력을 다르게 프레이밍한
// 것뿐이라 fetch·리스트 뼈대를 공유한다 — describe()만 바꿔 두 패널을 만든다.
export function RiskAlertPanel({ title, alerts, describe, emptyMessage }: RiskAlertPanelProps) {
  return (
    <DashboardCard title={title} meta={`${alerts.length}건`} bodyClassName="overflow-y-auto px-7 pb-7">
      {alerts.length === 0 ? (
        <p className="text-sm text-neutral-500">{emptyMessage}</p>
      ) : (
        <ul className="space-y-3">
          {alerts.map((a) => {
            const Icon = ALERT_TYPE_ICON[a.type]
            const tone = ALERT_TYPE_TONE[a.type]
            return (
              <li key={a.id} className="flex items-start gap-3">
                <Icon size={16} className={`mt-0.5 shrink-0 ${tone}`} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2 text-sm text-neutral-200">
                    <span className="truncate">{describe(a)}</span>
                    <span className="shrink-0 text-xs text-neutral-500">{formatTime(a.createdAt)}</span>
                  </div>
                  <div className="mt-0.5 text-xs text-neutral-600">{a.trackerId}</div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardCard>
  )
}
