import type { PredictionEpisodeSummary } from '../../types/adminMetrics'
import { DashboardCard } from '../dashboard/DashboardCard'

const formatCreatedAt = (iso: string) => new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })

interface ReportScenarioTableProps {
  episodes: PredictionEpisodeSummary[]
}

const STATUS_LABEL: Record<PredictionEpisodeSummary['status'], string> = {
  ACTIVE: '발효 중',
  CANCELED: '해제됨(오탐)',
  INVALIDATED: '무효화(급변)',
  EXPIRED: '만료(오탐)',
  BREACHED: '적중',
}

const BREACH_LABEL: Record<PredictionEpisodeSummary['status'], string> = {
  ACTIVE: '판정 대기',
  CANCELED: '아니오',
  INVALIDATED: '판정 불가',
  EXPIRED: '아니오',
  BREACHED: '예',
}

// 화면_탭_구성.md의 "예측 경고 여부" 컬럼은 만들지 않는다 — 이 표에 나오는 모든 행은 이미
// 예측 경고가 발행된 에피소드뿐이라(episodes[]의 정의) 그 컬럼은 항상 "예"가 되는 무의미한
// 칸이 된다. 대신 실제 판정에 필요한 상태·이탈 여부·리드타임만 정직하게 보여준다.
export function ReportScenarioTable({ episodes }: ReportScenarioTableProps) {
  // 백엔드 조회 순서가 명시적으로 정렬돼 있지 않아, "언제 발생했나" 표를 최근순으로 읽을 수
  // 있도록 프론트에서 createdAt 내림차순 정렬한다.
  const sorted = [...episodes].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  return (
    <DashboardCard
      title="시나리오 결과"
      meta={`예측 경고 발행된 에피소드 ${episodes.length}건`}
      bodyClassName="overflow-y-auto px-7 pb-4"
    >
      {episodes.length === 0 ? (
        <p className="text-sm text-neutral-500">해당 기간에 예측 에피소드가 없습니다.</p>
      ) : (
        <table className="w-full text-left text-[15px]">
          <thead>
            <tr className="text-sm text-neutral-500">
              <th className="pr-6 pb-2 font-normal">발생 시각</th>
              <th className="pr-6 pb-2 font-normal">화물 ID</th>
              <th className="pr-6 pb-2 font-normal">프로파일</th>
              <th className="pr-6 pb-2 font-normal">상태</th>
              <th className="pr-6 pb-2 font-normal">실제 이탈</th>
              <th className="pb-2 font-normal">리드타임</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((e, i) => (
              <tr key={`${e.trackerId}-${i}`} className="h-12 border-t border-divider">
                <td className="pr-6 py-2 text-neutral-400">{formatCreatedAt(e.createdAt)}</td>
                <td className="pr-6 py-2">{e.trackerId}</td>
                <td className="pr-6 py-2 text-neutral-400">{e.productName}</td>
                <td className="pr-6 py-2 text-neutral-300">{STATUS_LABEL[e.status]}</td>
                <td className="pr-6 py-2 text-neutral-300">{BREACH_LABEL[e.status]}</td>
                <td className="py-2 text-neutral-400">{e.leadTimeMinutes != null ? `${e.leadTimeMinutes}분` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </DashboardCard>
  )
}
