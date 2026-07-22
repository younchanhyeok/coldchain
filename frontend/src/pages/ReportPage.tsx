import { useState } from 'react'
import { getEvaluationRuns, getPredictionMetrics, hasAdminKey } from '../api/adminMetrics'
import { getSummary } from '../api/summary'
import { ReportExecutiveSummary } from '../components/report/ReportExecutiveSummary'
import { ReportKpiCards } from '../components/report/ReportKpiCards'
import { ReportModelComparison } from '../components/report/ReportModelComparison'
import { ReportRescueChart } from '../components/report/ReportRescueChart'
import { ReportScenarioTable } from '../components/report/ReportScenarioTable'
import type { EvaluationRun } from '../types/adminMetrics'
import { usePolling } from '../hooks/usePolling'

// "달력 기간" 선택은 정직하지 않다 — 데이터가 시뮬레이터 실행 시점에만 존재하고, 실시간 조회는
// 대시보드 온도 차트와 같은 프리셋 버튼 방식을 쓴다(null = "전체 기간"). "평가 런"을 선택하면
// 그 런이 고정한 기간·모델버전의 스냅샷 창을 대신 조회한다(M7 — 아래 셀렉터).
const PERIOD_OPTIONS: { label: string; hours: number | null }[] = [
  { label: '최근 1시간', hours: 1 },
  { label: '최근 24시간', hours: 24 },
  { label: '전체 기간', hours: null },
]

const formatRunLabel = (run: EvaluationRun) => {
  const version = run.modelVersion ?? '전체'
  const tag = run.label ?? (run.triggerType === 'SCHEDULED' ? '정시' : '수동')
  const when = new Date(run.periodEnd).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
  return `${version} · ${tag} · ~${when}`
}

export function ReportPage() {
  const [periodHours, setPeriodHours] = useState<number | null>(null)
  // null = 실시간(프리셋) 모드. 값이 있으면 그 평가 런의 불변 스냅샷 창을 본다.
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)

  const { data: runs } = usePolling(
    () => (hasAdminKey ? getEvaluationRuns(20) : Promise.resolve([])),
    30_000,
  )
  const runList = runs ?? []
  const selectedRun = selectedRunId != null ? runList.find((r) => r.id === selectedRunId) ?? null : null

  // from/to는 실시간 모드에서만 폴링 틱마다 새로 계산한다(열어둔 뒤 생긴 에피소드 반영). 런 모드는
  // 과거 고정 창이라 재조회해도 값이 같다 — 폴링 간격을 1시간으로 늘려 사실상 멈춘다(불변 스냅샷).
  const { data: metrics, error } = usePolling(
    () => {
      if (!hasAdminKey) return Promise.resolve(null)
      if (selectedRun) {
        return getPredictionMetrics({
          from: selectedRun.periodStart,
          to: selectedRun.periodEnd,
          modelVersion: selectedRun.modelVersion ?? undefined,
        })
      }
      const now = new Date()
      const from = periodHours == null ? new Date(0) : new Date(now.getTime() - periodHours * 60 * 60 * 1000)
      return getPredictionMetrics({ from: from.toISOString(), to: now.toISOString() })
    },
    selectedRun ? 60 * 60 * 1000 : 30_000,
    // selectedRun의 해소 여부(≠selectedRunId)까지 deps에 넣는다 — 선택한 런이 폴링 갱신으로
    // 최신 20개 밖으로 밀려 selectedRun이 null로 바뀌면 실시간(30초)으로 즉시 복귀하도록.
    [periodHours, selectedRunId, selectedRun != null],
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
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex gap-1">
            {PERIOD_OPTIONS.map((opt) => (
              <button
                key={opt.label}
                type="button"
                disabled={selectedRun != null}
                className={`rounded-md px-3 py-1.5 text-xs disabled:opacity-40 ${
                  periodHours === opt.hours && selectedRun == null
                    ? 'bg-primary/20 text-primary'
                    : 'text-neutral-500 hover:bg-card-hover'
                }`}
                onClick={() => setPeriodHours(opt.hours)}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <select
            // 해소된 런이 없으면(만료·목록 밖) '실시간'을 표시 — 상태의 유령 선택이 UI에 남지 않게.
            value={selectedRun ? selectedRunId ?? '' : ''}
            onChange={(e) => setSelectedRunId(e.target.value ? Number(e.target.value) : null)}
            className="rounded-md border border-border bg-card px-2 py-1.5 text-xs text-neutral-300"
          >
            <option value="">실시간 (프리셋 기간)</option>
            {runList.map((run) => (
              <option key={run.id} value={run.id}>
                {formatRunLabel(run)}
              </option>
            ))}
          </select>
        </div>
        <span className="text-xs text-neutral-500">
          {metrics?.modelVersion ?? '—'} 기준 · {selectedRun ? '평가 런 스냅샷' : '실시간'}
        </span>
      </div>

      <ReportKpiCards metrics={metrics} rescuedByPrediction={summary?.rescuedByPrediction ?? null} />
      <ReportExecutiveSummary metrics={metrics} />
      <ReportModelComparison runs={runList} />
      <ReportScenarioTable episodes={metrics?.episodes ?? []} />
      <ReportRescueChart episodes={metrics?.episodes ?? []} />
    </div>
  )
}
