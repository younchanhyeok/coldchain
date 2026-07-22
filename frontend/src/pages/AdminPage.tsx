import { Building2, Radio } from 'lucide-react'
import { getAdminOverview, getEvaluationRuns, getPredictionMetrics, hasAdminKey } from '../api/adminMetrics'
import { DashboardCard } from '../components/dashboard/DashboardCard'
import { KpiTile } from '../components/dashboard/KpiTile'
import { ReportExecutiveSummary } from '../components/report/ReportExecutiveSummary'
import { ReportKpiCards } from '../components/report/ReportKpiCards'
import { ReportRescueChart } from '../components/report/ReportRescueChart'
import { ReportScenarioTable } from '../components/report/ReportScenarioTable'
import { usePolling } from '../hooks/usePolling'

/**
 * 관리자 뷰(v1) — 시스템 전체 집계. 리포트 탭(화주 자신의 배송 집계)과는 다른 화면·범위로,
 * 리포트 탭을 이 화면으로 대체하거나 흡수하지 않는다("모델 지표 이동" 제안 기각 — M4에서
 * 확정한 화주 화면을 해체할 이유가 없다). 여기 지표는 화주별 스코프 분리 없이 시스템 전체
 * 집계다(v2로 이연 — README에 명시 예정).
 *
 * 로컬 전용 도구다 — 어드민 키가 프론트 번들에 그대로 새겨지므로(adminMetrics.ts 주석 참고)
 * 이 화면을 포함한 프론트를 공개 배포해선 안 된다. 인증 없는 공개 배포가 필요해지면 서버사이드
 * 프록시나 별도 어드민 로그인이 있어야 하는데, 그게 "어드민 화면 v2" 이연의 실제 근거다.
 */
export function AdminPage() {
  const { data: overview } = usePolling(() => (hasAdminKey ? getAdminOverview() : Promise.resolve(null)), 30_000)
  const { data: metrics, error } = usePolling(() => {
    if (!hasAdminKey) return Promise.resolve(null)
    return getPredictionMetrics({ from: new Date(0).toISOString(), to: new Date().toISOString() })
  }, 30_000)
  // 최신순 → [0]이 가장 최근 런. 여기선 "가장 최근 런 한 줄 요약"만 — v1/v2 나란히 비교·에피소드
  // 상세는 리포트 탭이 소유한다(관리자 화면은 요약, 상세 흡수 금지).
  const { data: runs } = usePolling(() => (hasAdminKey ? getEvaluationRuns(1) : Promise.resolve([])), 30_000)
  const latestRun = runs?.[0] ?? null
  if (!hasAdminKey) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-body px-6 text-center text-sm text-neutral-500">
        관리자 키가 설정되지 않았습니다 (VITE_ADMIN_KEY).
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-body px-6 text-center text-danger">
        관리자 지표를 불러오지 못했습니다: {error.message}
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-body px-6 py-8 text-neutral-100">
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">❄ ColdChain 관리자</h1>
          <p className="mt-1 text-xs text-neutral-500">
            시스템 전체 집계 · 예측 {metrics?.modelVersion ?? 'v1-linear'} 기준 (화주별 스코프 분리 없음, v2 예정)
          </p>
        </div>

        <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
          <KpiTile
            icon={Building2}
            tone="primary"
            label="고객사 수"
            value={overview != null ? String(overview.shipperCount) : '—'}
            sub="등록된 화주 계정"
            hasValue={overview != null}
          />
          <KpiTile
            icon={Radio}
            tone="success"
            label="활성 트래커 수"
            value={overview != null ? String(overview.activeTrackerCount) : '—'}
            sub="배송 완료 전(READY·IN_TRANSIT)인 트래커"
            hasValue={overview != null}
          />
        </div>

        {latestRun && (
          <DashboardCard title="최신 평가 런" meta="상세·모델 비교는 리포트 탭">
            <div className="flex flex-wrap items-center gap-x-8 gap-y-2 text-sm">
              <span className="text-neutral-400">
                모델 <span className="text-neutral-200">{latestRun.modelVersion ?? '전체'}</span>
              </span>
              <span className="text-neutral-400">
                적중률 <span className="text-neutral-200">{Math.round(latestRun.hitRate * 100)}%</span>
              </span>
              <span className="text-neutral-400">
                오탐률 <span className="text-neutral-200">{Math.round(latestRun.falsePositiveRate * 100)}%</span>
              </span>
              <span className="text-neutral-400">
                시각오차{' '}
                <span className="text-neutral-200">
                  {latestRun.avgBreachTimingErrorMinutes != null
                    ? `${latestRun.avgBreachTimingErrorMinutes.toFixed(1)}분`
                    : '—'}
                </span>
              </span>
              <span className="ml-auto text-xs text-neutral-500">
                {new Date(latestRun.createdAt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })}
              </span>
            </div>
          </DashboardCard>
        )}

        {/* rescuedByPrediction은 GET /summary(화주별 스코프, JWT 필요)에서만 나온다 — 화주
            비의존 어드민 화면과 인가 축이 달라 여기선 집계하지 않는다(시스템 전체 합산 API가
            아직 없음, v2 이연). null로 두면 KpiTile이 "—"로 정직하게 표시한다. */}
        <ReportKpiCards metrics={metrics} rescuedByPrediction={null} />
        <ReportExecutiveSummary metrics={metrics} />
        <ReportScenarioTable episodes={metrics?.episodes ?? []} />
        <ReportRescueChart episodes={metrics?.episodes ?? []} />
      </div>
    </div>
  )
}
