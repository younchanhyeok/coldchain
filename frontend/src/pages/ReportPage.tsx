import { useMemo, useState } from 'react'
import { getPredictionMetrics, hasAdminKey } from '../api/adminMetrics'
import { getSummary } from '../api/summary'
import { ReportExecutiveSummary } from '../components/report/ReportExecutiveSummary'
import { ReportKpiCards } from '../components/report/ReportKpiCards'
import { ReportRescueChart } from '../components/report/ReportRescueChart'
import { ReportScenarioTable } from '../components/report/ReportScenarioTable'
import { usePolling } from '../hooks/usePolling'

// "달력 기간" 선택은 정직하지 않다 — 데이터가 시뮬레이터 실행 시점에만 존재하고, 아직 "평가 런"을
// 별도로 기록하는 백엔드 개념이 없다(화면_탭_구성.md). 그래서 임의 날짜 범위 대신 대시보드
// 온도 차트와 같은 프리셋 버튼 방식을 쓴다 — null은 "전체 기간"(처음부터 지금까지).
const PERIOD_OPTIONS: { label: string; hours: number | null }[] = [
  { label: '최근 1시간', hours: 1 },
  { label: '최근 24시간', hours: 24 },
  { label: '전체 기간', hours: null },
]

export function ReportPage() {
  const [periodHours, setPeriodHours] = useState<number | null>(null)

  const { from, to } = useMemo(() => {
    const now = new Date()
    const from = periodHours == null ? new Date(0) : new Date(now.getTime() - periodHours * 60 * 60 * 1000)
    return { from: from.toISOString(), to: now.toISOString() }
  }, [periodHours])

  const { data: metrics, error } = usePolling(
    () => (hasAdminKey ? getPredictionMetrics({ from, to }) : Promise.resolve(null)),
    30_000,
    [from, to],
  )
  const { data: summary } = usePolling(() => getSummary(), 30_000)

  if (!hasAdminKey) {
    return (
      <div className="flex min-h-[300px] items-center justify-center rounded-card border border-border bg-card text-sm text-neutral-500 shadow-card">
        관리자 키가 설정되지 않았습니다 (VITE_ADMIN_KEY). 리포트는 예측 평가지표 API 전용이라 키
        없이는 조회할 수 없습니다.
      </div>
    )
  }

  if (error) {
    return <div className="text-danger">평가지표를 불러오지 못했습니다: {error.message}</div>
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex gap-1">
          {PERIOD_OPTIONS.map((opt) => (
            <button
              key={opt.label}
              type="button"
              className={`rounded-md px-3 py-1.5 text-xs ${
                periodHours === opt.hours ? 'bg-primary/20 text-primary' : 'text-neutral-500 hover:bg-card-hover'
              }`}
              onClick={() => setPeriodHours(opt.hours)}
            >
              {opt.label}
            </button>
          ))}
        </div>
        <span className="text-xs text-neutral-500">
          {metrics?.modelVersion ?? 'v1-linear'} 기준 · M7에서 v2 모델과 비교 예정
        </span>
      </div>

      <ReportKpiCards metrics={metrics} rescuedByPrediction={summary?.rescuedByPrediction ?? null} />
      <ReportExecutiveSummary metrics={metrics} />
      <ReportScenarioTable episodes={metrics?.episodes ?? []} />
      <ReportRescueChart episodes={metrics?.episodes ?? []} />
    </div>
  )
}
