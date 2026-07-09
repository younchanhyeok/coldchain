import { CheckCircle2, Truck } from 'lucide-react'
import { useMemo } from 'react'
import { ALERT_TYPE_ICON, ALERT_TYPE_LABEL, ALERT_TYPE_TONE } from '../../lib/alertPresentation'
import type { Alert } from '../../types/alert'
import type { ShipmentSummary } from '../../types/shipment'
import { DashboardCard } from '../dashboard/DashboardCard'

interface LiveOpsRecentEventsProps {
  shipment: ShipmentSummary | null
  alerts: Alert[]
}

type TimelineEvent = {
  ts: string
  label: string
  icon: typeof Truck
  tone: string
}

// 실존 이벤트만 조립한다 — shipment 자체의 전이 시각(출발·도착)과 alert 이력(이탈·이상 감지)뿐,
// "지오펜스 통과"·"온도 정상 유지" 같은 무이벤트성 창작은 화면_탭_구성.md 거부 목록.
export function LiveOpsRecentEvents({ shipment, alerts }: LiveOpsRecentEventsProps) {
  const events = useMemo<TimelineEvent[]>(() => {
    const list: TimelineEvent[] = []
    if (shipment?.inTransitAt) {
      list.push({ ts: shipment.inTransitAt, label: '배송 출발', icon: Truck, tone: 'text-primary' })
    }
    if (shipment?.deliveredAt) {
      list.push({ ts: shipment.deliveredAt, label: '배송 완료', icon: CheckCircle2, tone: 'text-success' })
    }
    for (const a of alerts) {
      list.push({
        ts: a.createdAt,
        label: ALERT_TYPE_LABEL[a.type],
        icon: ALERT_TYPE_ICON[a.type],
        tone: ALERT_TYPE_TONE[a.type],
      })
    }
    return list.sort((x, y) => new Date(y.ts).getTime() - new Date(x.ts).getTime()).slice(0, 8)
  }, [shipment, alerts])

  return (
    <DashboardCard title="최근 배송 이벤트">
      {events.length === 0 ? (
        <p className="text-sm text-neutral-500">최근 이벤트가 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {events.map((e, i) => (
            <li key={i} className="flex items-center gap-3">
              <e.icon size={16} className={`shrink-0 ${e.tone}`} />
              <span className="flex-1 text-sm text-neutral-200">{e.label}</span>
              <span className="text-xs text-neutral-500">{new Date(e.ts).toLocaleString('ko-KR')}</span>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  )
}
