import { Package, TrendingUp, TriangleAlert, Truck } from 'lucide-react'
import type { SummaryResponse } from '../../types/summary'
import type { TrackerSummary } from '../../types/tracker'
import { KpiTile } from './KpiTile'

interface KpiTilesProps {
  trackers: TrackerSummary[]
  summary: SummaryResponse | null
}

export function KpiTiles({ trackers, summary }: KpiTilesProps) {
  const inTransitCount = trackers.length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  // 이탈률 = 진행 중 배송 중 현재 이탈 상태인 비율(스냅샷) — GET /summary의 기존 필드 두 개로
  // 프론트에서 계산한다(신규 백엔드 집계 불필요). 진행 중 배송이 없으면 정의 자체가 안 되므로 "—".
  const breachRate = summary && summary.inTransit > 0
    ? `${Math.round((summary.breachCount / summary.inTransit) * 100)}%`
    : null

  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <KpiTile
        icon={Truck}
        tone="primary"
        label="배송 중"
        value={String(inTransitCount)}
        sub="조회된 트래커 수"
        hasValue
      />
      <KpiTile
        icon={TriangleAlert}
        tone="warning"
        label="이탈 위험"
        value={String(breachCount)}
        sub="임계 온도 초과"
        hasValue
      />
      <KpiTile
        icon={Package}
        tone="success"
        label="구조된 박스"
        value={summary ? String(summary.rescuedByPrediction) : '—'}
        sub="예측 경고 후 이탈 없이 종료된 건수"
        hasValue={summary != null}
      />
      <KpiTile
        icon={TrendingUp}
        tone="danger"
        label="이탈률"
        value={breachRate ?? '—'}
        sub="진행 중 배송 중 이탈 비율(스냅샷)"
        hasValue={breachRate != null}
      />
    </div>
  )
}
