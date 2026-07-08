import type { TrackerDetail } from '../../types/tracker'
import { DashboardCard } from '../dashboard/DashboardCard'

interface CargoShipmentInfoProps {
  tracker: TrackerDetail
}

export function CargoShipmentInfo({ tracker }: CargoShipmentInfoProps) {
  return (
    <DashboardCard title="배송 정보">
      <dl className="grid grid-cols-2 gap-y-3 text-sm">
        <dt className="text-neutral-500">노선</dt>
        <dd className="text-neutral-200">
          {tracker.originName && tracker.destinationName
            ? `${tracker.originName} → ${tracker.destinationName}`
            : '—'}
        </dd>

        <dt className="text-neutral-500">기사 연락처</dt>
        {/* 기사 이름 필드는 없음 — 연락처로만 표시(화면_탭_구성.md 거부 목록) */}
        <dd className="text-neutral-200">{tracker.shipment?.driverContact ?? '—'}</dd>
      </dl>
    </DashboardCard>
  )
}
