import { AlertTriangle, CheckCircle2, Send } from 'lucide-react'
import { useMemo } from 'react'
import type { Alert } from '../../types/alert'
import { KpiTile } from '../dashboard/KpiTile'

interface AlertsKpiTilesProps {
  alerts: Alert[]
  /** 전체 알림이 조회 상한(200건)을 넘어 KPI가 최근분만 집계 중인지 — true면 라벨에 정직하게 명시. */
  sampleLimited: boolean
}

function isToday(iso: string): boolean {
  return new Date(iso).toDateString() === new Date().toDateString()
}

// 화면_탭_구성.md: "미확인/처리 완료"는 ack 워크플로 전제라 보류 — 실존 데이터(오늘 발생/성공/
// 실패·재시도)만 표시한다.
export function AlertsKpiTiles({ alerts, sampleLimited }: AlertsKpiTilesProps) {
  const { todayCount, sentCount, failedCount } = useMemo(() => {
    const today = alerts.filter((a) => isToday(a.createdAt))
    return {
      todayCount: today.length,
      sentCount: alerts.filter((a) => a.status === 'SENT').length,
      failedCount: alerts.filter((a) => a.status === 'FAILED').length,
    }
  }, [alerts])

  const note = sampleLimited ? ` · 최근 ${alerts.length}건 기준` : ''

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
      <KpiTile
        icon={AlertTriangle}
        tone="primary"
        label="오늘 발생"
        value={String(todayCount)}
        sub={`오늘 기록된 알림${note}`}
        hasValue
      />
      <KpiTile
        icon={CheckCircle2}
        tone="success"
        label="발송 성공"
        value={String(sentCount)}
        sub={`SENT 상태${note}`}
        hasValue
      />
      <KpiTile
        icon={Send}
        tone="danger"
        label="발송 실패·재시도"
        value={String(failedCount)}
        sub={`FAILED 상태(재시도 소진 포함)${note}`}
        hasValue
      />
    </div>
  )
}
