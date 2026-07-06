import type { TrackerSummary } from '../../types/tracker'

interface RiskListProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

function overThreshold(t: TrackerSummary): number {
  if (t.lastTemperature == null) return -Infinity
  return t.lastTemperature - t.thresholdTemp
}

export function RiskList({ trackers, selectedTrackerId, onSelectTracker }: RiskListProps) {
  const risky = trackers.filter((t) => t.status === 'BREACH').sort((a, b) => overThreshold(b) - overThreshold(a))

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-sm font-semibold text-slate-200">이탈 위험 화물 리스트</h2>

      {risky.length === 0 ? (
        <p className="text-sm text-slate-500">현재 이탈 위험 화물이 없습니다.</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="text-slate-500">
              <th className="pb-2 font-normal">화물 ID</th>
              <th className="pb-2 font-normal">노선</th>
              <th className="pb-2 font-normal">현재 온도</th>
              <th className="pb-2 font-normal">임계값</th>
              <th className="pb-2 font-normal" />
            </tr>
          </thead>
          <tbody>
            {risky.map((t) => (
              <tr
                key={t.trackerId}
                className={`cursor-pointer border-t border-slate-800 hover:bg-slate-800/50 ${
                  t.trackerId === selectedTrackerId ? 'bg-slate-800/70' : ''
                }`}
                onClick={() => onSelectTracker(t.trackerId)}
              >
                <td className="py-2">{t.trackerId}</td>
                <td className="py-2 text-slate-400">
                  {t.originName && t.destinationName ? `${t.originName} → ${t.destinationName}` : '—'}
                </td>
                <td className="py-2 text-red-400">{t.lastTemperature?.toFixed(1)}℃</td>
                <td className="py-2 text-slate-400">{t.thresholdTemp.toFixed(1)}℃</td>
                <td className="py-2">
                  <span className="rounded bg-red-500/20 px-2 py-0.5 text-xs font-medium text-red-400">
                    BREACH
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* 전체 화물 관리 화면은 아직 없음 — 클릭 동작 없는 span으로 명확히 구분 */}
      <span
        className="mt-3 inline-block cursor-default select-none text-xs text-slate-600"
        title="아직 준비되지 않은 화면입니다"
      >
        전체 보기 →
      </span>
    </div>
  )
}
