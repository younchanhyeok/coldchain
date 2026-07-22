import type { EvaluationRun } from '../../types/adminMetrics'
import { DashboardCard } from '../dashboard/DashboardCard'

const formatPercent = (ratio: number) => `${Math.round(ratio * 100)}%`
const formatMinutes = (value: number | null) => (value != null ? `${value.toFixed(1)}분` : '—')
const formatPeriod = (run: EvaluationRun) =>
  `${new Date(run.periodStart).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })}` +
  ` ~ ${new Date(run.periodEnd).toLocaleTimeString('ko-KR', { timeStyle: 'short' })}`

interface ReportModelComparisonProps {
  runs: EvaluationRun[]
}

// v1/v2 각 모델의 "최신" 평가 런을 나란히 놓아 수치만 대조한다 — 어느 쪽이 낫다는 판정·강조는
// 넣지 않는다(모델 우열 서사는 M7 비교 리포트/개발정리의 몫, 화면은 정직하게 숫자만).
// 한쪽 버전 런이 아직 없으면 그 열은 "런 없음"으로 표시한다.
export function ReportModelComparison({ runs }: ReportModelComparisonProps) {
  const latestOf = (version: string) =>
    runs
      .filter((run) => run.modelVersion === version)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0] ?? null

  const v1 = latestOf('v1-linear')
  const v2 = latestOf('v2-newton')

  if (!v1 && !v2) {
    return (
      <DashboardCard title="모델 비교 (v1 vs v2)">
        <p className="text-sm text-neutral-500">
          아직 기록된 평가 런이 없습니다. 관리자 API로 수동 런을 만들거나 매시 스케줄 스냅샷이
          쌓이면 여기에 v1·v2 수치가 나란히 표시됩니다.
        </p>
      </DashboardCard>
    )
  }

  const rows: { label: string; value: (run: EvaluationRun) => string }[] = [
    { label: '평가 기간', value: formatPeriod },
    { label: '예측 수', value: (run) => String(run.totalPredictions) },
    { label: '적중(TP)', value: (run) => String(run.truePositives) },
    { label: '오탐(FP)', value: (run) => String(run.falsePositives) },
    { label: '놓침(missed)', value: (run) => String(run.missedBreaches) },
    { label: '적중률', value: (run) => formatPercent(run.hitRate) },
    { label: '오탐률', value: (run) => formatPercent(run.falsePositiveRate) },
    { label: '평균 리드타임', value: (run) => formatMinutes(run.avgLeadTimeMinutes) },
    { label: '시각오차 |예측−실제|', value: (run) => formatMinutes(run.avgBreachTimingErrorMinutes) },
  ]

  const cell = (run: EvaluationRun | null, value: (run: EvaluationRun) => string) =>
    run ? value(run) : '런 없음'

  return (
    <DashboardCard
      title="모델 비교 (v1 vs v2)"
      meta="각 모델의 최신 평가 런 기준"
      footer={
        <p className="text-xs text-neutral-500">
          ※ 두 런은 서로 다른 기간·시나리오의 최신 스냅샷이라 통제된 1:1 비교가 아니다(모델은
          프로세스 단위 토글이라 한 창에 한 모델만 돈다). 같은 시나리오·시드로 짝지은 통제 비교는
          개발정리_M7의 v1 vs v2 리포트를 따른다.
        </p>
      }
    >
      <table className="w-full text-left text-[15px]">
        <thead>
          <tr className="text-sm text-neutral-500">
            <th className="pr-6 pb-2 font-normal">지표</th>
            <th className="pr-6 pb-2 font-normal">v1-linear</th>
            <th className="pb-2 font-normal">v2-newton</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="h-11 border-t border-divider">
              <td className="pr-6 py-2 text-neutral-400">{row.label}</td>
              <td className="pr-6 py-2 text-neutral-200">{cell(v1, row.value)}</td>
              <td className="py-2 text-neutral-200">{cell(v2, row.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </DashboardCard>
  )
}
