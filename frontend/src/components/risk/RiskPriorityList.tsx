import { formatLeadTimeMinutes } from '../../lib/leadTime'
import type { TrackerSummary } from '../../types/tracker'
import { DashboardCard } from '../dashboard/DashboardCard'

interface RiskPriorityListProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

const formatSlope = (slope: number) => `${slope > 0 ? '+' : ''}${slope.toFixed(2)}℃/분`

// "지금 당장 어떤 화물을 조치해야 하는가"를 5초 안에 판단하는 화면의 정체성 — 정렬 기준이
// 곧 우선순위다(예상 이탈 임박순, predictedBreachAt 오름차순). 위험도%·신뢰도%는 v1 선형회귀가
// 확률을 산출하지 않으므로 절대 만들어 넣지 않는다(화면_탭_구성.md 거부 목록).
export function RiskPriorityList({ trackers, selectedTrackerId, onSelectTracker }: RiskPriorityListProps) {
  const prioritized = trackers
    .filter((t) => t.activePrediction != null)
    .sort((a, b) => new Date(a.activePrediction!.predictedBreachAt).getTime() - new Date(b.activePrediction!.predictedBreachAt).getTime())

  return (
    <DashboardCard title="AI 위험 우선순위" meta="예상 이탈 임박순" bodyClassName="overflow-y-auto px-7 pb-4">
      {prioritized.length === 0 ? (
        <p className="text-sm text-neutral-500">현재 발효 중인 예측 경고가 없습니다.</p>
      ) : (
        <table className="w-full text-left text-[15px]">
          <thead>
            <tr className="text-sm text-neutral-500">
              <th className="pr-6 pb-2 font-normal">화물 ID</th>
              <th className="pr-6 pb-2 font-normal">예상 이탈까지</th>
              <th className="pr-6 pb-2 font-normal">현재 → 예측 온도</th>
              <th className="pb-2 font-normal">기울기</th>
            </tr>
          </thead>
          <tbody>
            {prioritized.map((t) => (
              <tr
                key={t.trackerId}
                className={`h-14 cursor-pointer border-t border-divider hover:bg-table-row-hover ${
                  t.trackerId === selectedTrackerId
                    ? 'border-l-2 border-l-primary bg-card-hover'
                    : 'border-l-2 border-l-transparent'
                }`}
                onClick={() => onSelectTracker(t.trackerId)}
              >
                <td className="pr-6 py-2">{t.trackerId}</td>
                <td className="pr-6 py-2 font-medium text-warning">
                  {formatLeadTimeMinutes(t.activePrediction!.leadTimeMinutes)}
                </td>
                <td className="pr-6 py-2 text-neutral-300">
                  {t.lastTemperature?.toFixed(1) ?? '—'}℃ → {t.thresholdTemp.toFixed(1)}℃
                </td>
                <td className="py-2 text-neutral-400">{formatSlope(t.activePrediction!.slopePerMinute)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </DashboardCard>
  )
}
