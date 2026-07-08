import type { TrackerSummary } from '../../types/tracker'
import { DashboardCard } from './DashboardCard'

interface RiskListProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
  onViewAll: () => void
}

function overThreshold(t: TrackerSummary): number {
  if (t.lastTemperature == null) return -Infinity
  return t.lastTemperature - t.thresholdTemp
}

function temperatureToneClass(t: TrackerSummary): string {
  if (t.lastTemperature != null && t.lastTemperature > t.thresholdTemp) return 'text-danger'
  return 'text-primary'
}

// BREACH가 CAUTION보다 항상 위 — 같은 등급 안에서는 임계 초과폭이 큰 순.
function riskRank(t: TrackerSummary): number {
  return t.status === 'BREACH' ? 1 : 0
}

export function RiskList({ trackers, selectedTrackerId, onSelectTracker, onViewAll }: RiskListProps) {
  // M3부터 CAUTION(활성 이상탐지)도 포함 — BREACH만이 아니라 "이상 감지됨"까지 위험 리스트에 노출.
  const risky = trackers
    .filter((t) => t.status === 'BREACH' || t.status === 'CAUTION')
    .sort((a, b) => riskRank(b) - riskRank(a) || overThreshold(b) - overThreshold(a))

  // M3부터 화물 관리 탭이 생겨 실제 이동 가능 — 이전엔 클릭 동작 없는 span이었다.
  const footer = (
    <button
      type="button"
      onClick={onViewAll}
      className="inline-block text-xs text-primary hover:underline"
    >
      전체 보기 →
    </button>
  )

  return (
    <DashboardCard title="이탈 위험 화물 리스트" footer={footer} bodyClassName="overflow-y-auto px-7 pb-4">
      {risky.length === 0 ? (
        <p className="text-sm text-neutral-500">현재 이탈 위험 화물이 없습니다.</p>
      ) : (
        <table className="w-full text-left text-[15px]">
          <thead>
            <tr className="text-sm text-neutral-500">
              <th className="pr-6 pb-2 font-normal">화물 ID</th>
              <th className="pr-6 pb-2 font-normal">노선</th>
              <th className="pr-6 pb-2 font-normal">현재 온도</th>
              <th className="pr-6 pb-2 font-normal">임계값</th>
              <th className="pb-2 font-normal" />
            </tr>
          </thead>
          <tbody>
            {risky.map((t) => (
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
                <td className="pr-6 py-2 text-neutral-400">
                  {t.originName && t.destinationName ? `${t.originName} → ${t.destinationName}` : '—'}
                </td>
                <td className={`pr-6 py-2 font-medium ${temperatureToneClass(t)}`}>
                  {t.lastTemperature?.toFixed(1)}℃
                </td>
                <td className="pr-6 py-2 text-neutral-400">{t.thresholdTemp.toFixed(1)}℃</td>
                <td className="py-2">
                  {t.status === 'BREACH' ? (
                    <span className="rounded-full bg-danger/15 px-2.5 py-1 text-xs font-medium text-danger">
                      이탈
                    </span>
                  ) : (
                    <span className="rounded-full bg-warning/15 px-2.5 py-1 text-xs font-medium text-warning">
                      주의
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </DashboardCard>
  )
}
