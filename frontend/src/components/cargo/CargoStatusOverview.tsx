import type { TrackerDetail } from '../../types/tracker'
import { DashboardCard } from '../dashboard/DashboardCard'

interface CargoStatusOverviewProps {
  tracker: TrackerDetail
  etaMinutes: number | null
  breachStartedAt: string | null
}

const STATUS_LABEL: Record<string, { label: string; className: string }> = {
  SAFE: { label: '정상', className: 'bg-primary/15 text-primary' },
  CAUTION: { label: '주의', className: 'bg-warning/15 text-warning' },
  BREACH: { label: '이탈', className: 'bg-danger/15 text-danger' },
  RISK: { label: '예측 위험', className: 'bg-warning/15 text-warning' },
}

export function CargoStatusOverview({ tracker, etaMinutes, breachStartedAt }: CargoStatusOverviewProps) {
  const isDelivered = tracker.shipment?.status === 'DELIVERED'
  const badge = isDelivered
    ? { label: '완료', className: 'bg-neutral-700/40 text-neutral-300' }
    : (STATUS_LABEL[tracker.status] ?? { label: tracker.status, className: 'bg-neutral-700/40 text-neutral-300' })

  return (
    <DashboardCard title="상태 개요" status={<span className={`rounded-full px-2.5 py-1 text-xs font-medium ${badge.className}`}>{badge.label}</span>}>
      <dl className="grid grid-cols-2 gap-y-3 text-sm">
        <dt className="text-neutral-500">현재 온도</dt>
        <dd className={tracker.status === 'BREACH' ? 'text-danger' : 'text-neutral-200'}>
          {tracker.lastTemperature != null ? `${tracker.lastTemperature.toFixed(1)}℃` : '—'}
          <span className="ml-1 text-xs text-neutral-500">/ 임계 {tracker.thresholdTemp.toFixed(1)}℃</span>
        </dd>

        <dt className="text-neutral-500">이탈 시작</dt>
        <dd className="text-neutral-200">
          {tracker.status === 'BREACH' && breachStartedAt ? new Date(breachStartedAt).toLocaleString('ko-KR') : '—'}
        </dd>

        <dt className="text-neutral-500">마지막 수신</dt>
        <dd className="text-neutral-200">
          {tracker.lastReportedAt ? new Date(tracker.lastReportedAt).toLocaleString('ko-KR') : '—'}
        </dd>

        <dt className="text-neutral-500">도착 예상</dt>
        <dd className="text-neutral-200">
          {etaMinutes != null ? `약 ${Math.round(etaMinutes)}분 후` : '계산 불가'}
        </dd>
      </dl>
    </DashboardCard>
  )
}
