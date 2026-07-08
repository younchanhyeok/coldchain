import type { ShipmentSummary } from '../../types/shipment'
import { DashboardCard } from '../dashboard/DashboardCard'
import { CargoStatusBadge } from './CargoStatusBadge'

interface CargoListProps {
  shipments: ShipmentSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

export function CargoList({ shipments, selectedTrackerId, onSelectTracker }: CargoListProps) {
  return (
    <DashboardCard title="화물 목록" meta={`${shipments.length}건`} bodyClassName="overflow-y-auto px-4 pb-4">
      {shipments.length === 0 ? (
        <p className="px-3 py-6 text-sm text-neutral-500">조건에 맞는 화물이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {shipments.map((s) => (
            <li key={s.shipmentId}>
              <button
                type="button"
                onClick={() => onSelectTracker(s.trackerId)}
                className={`w-full rounded-md border px-4 py-3 text-left transition-colors duration-150 ${
                  s.trackerId === selectedTrackerId
                    ? 'border-l-2 border-l-primary border-y-border border-r-border bg-card-hover'
                    : 'border-l-2 border-l-transparent border-y-border border-r-border hover:bg-table-row-hover'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-neutral-100">{s.trackerId}</span>
                  <CargoStatusBadge shipment={s} />
                </div>
                <div className="mt-1 text-xs text-neutral-400">
                  {s.originName && s.destinationName ? `${s.originName} → ${s.destinationName}` : '—'}
                </div>
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className="text-neutral-500">
                    현재 {s.lastTemperature != null ? `${s.lastTemperature.toFixed(1)}℃` : '—'}
                  </span>
                  <span className="text-neutral-600" title="M4 예측 이후 제공">
                    예측 — M4 제공
                  </span>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  )
}
