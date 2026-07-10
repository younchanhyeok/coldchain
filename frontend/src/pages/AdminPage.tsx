import { Building2, Radio } from 'lucide-react'
import { getAdminOverview, getPredictionMetrics, hasAdminKey } from '../api/adminMetrics'
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
 */
export function AdminPage() {
  const { data: overview } = usePolling(() => (hasAdminKey ? getAdminOverview() : Promise.resolve(null)), 30_000)
  const { data: metrics, error } = usePolling(() => {
    if (!hasAdminKey) return Promise.resolve(null)
    return getPredictionMetrics({ from: new Date(0).toISOString(), to: new Date().toISOString() })
  }, 30_000)
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
