import type { ShipmentSummary } from '../../types/shipment'

const TRACKER_STATUS_LABEL: Record<string, { label: string; className: string }> = {
  SAFE: { label: '정상', className: 'bg-primary/15 text-primary' },
  CAUTION: { label: '주의', className: 'bg-warning/15 text-warning' },
  BREACH: { label: '이탈', className: 'bg-danger/15 text-danger' },
  RISK: { label: '예측 위험', className: 'bg-warning/15 text-warning' },
}

// shipmentStatus=DELIVERED가 항상 우선 — 트래커는 재사용되므로 완료 후엔 트래커 상태가
// 다음 배송 것으로 바뀌어 있을 수 있다(화면_탭_구성.md).
export function CargoStatusBadge({ shipment }: { shipment: ShipmentSummary }) {
  if (shipment.shipmentStatus === 'DELIVERED') {
    return (
      <span className="rounded-full bg-neutral-700/40 px-2.5 py-1 text-xs font-medium text-neutral-300">완료</span>
    )
  }

  const tone = shipment.trackerStatus ? TRACKER_STATUS_LABEL[shipment.trackerStatus] : null
  if (!tone) {
    return <span className="rounded-full bg-neutral-700/40 px-2.5 py-1 text-xs font-medium text-neutral-400">—</span>
  }

  return <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${tone.className}`}>{tone.label}</span>
}
