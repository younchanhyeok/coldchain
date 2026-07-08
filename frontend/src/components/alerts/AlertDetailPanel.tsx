import { CheckCircle2, Circle, XCircle } from 'lucide-react'
import type { Alert } from '../../types/alert'
import { DashboardCard } from '../dashboard/DashboardCard'

interface AlertDetailPanelProps {
  alert: Alert | null
}

const TYPE_LABEL: Record<Alert['type'], string> = {
  BREACH: '임계 이탈',
  ANOMALY: '이상 감지',
}

const STATUS_LABEL: Record<Alert['status'], string> = {
  PENDING: '발송 중',
  SENT: '발송 성공',
  FAILED: '발송 실패',
}

export function AlertDetailPanel({ alert }: AlertDetailPanelProps) {
  if (!alert) {
    return (
      <div className="flex min-h-[300px] items-center justify-center rounded-card border border-border bg-card text-sm text-neutral-500 shadow-card">
        좌측에서 알림을 선택하세요.
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <DashboardCard title="알림 상세">
        <dl className="grid grid-cols-2 gap-y-3 text-sm">
          <dt className="text-neutral-500">트래커</dt>
          <dd className="text-neutral-200">{alert.trackerId}</dd>

          <dt className="text-neutral-500">발생 시각</dt>
          <dd className="text-neutral-200">{new Date(alert.createdAt).toLocaleString('ko-KR')}</dd>

          <dt className="text-neutral-500">발생 당시 온도</dt>
          <dd className="text-neutral-200">
            {alert.temperatureAtEvent != null ? `${alert.temperatureAtEvent.toFixed(1)}℃` : '—'}
          </dd>

          <dt className="text-neutral-500">유형</dt>
          <dd className="text-neutral-200">{TYPE_LABEL[alert.type]}</dd>

          {/* 채널 라벨은 정직하게 [SLACK]만 — SMS/알림톡은 미구현(FR-7 문서 그대로) */}
          <dt className="text-neutral-500">채널</dt>
          <dd className="text-neutral-200">[SLACK]</dd>

          <dt className="text-neutral-500">발송 상태</dt>
          <dd className="text-neutral-200">{STATUS_LABEL[alert.status]}</dd>
        </dl>
      </DashboardCard>

      <DashboardCard title="발송 타임라인">
        <AlertTimeline alert={alert} />
      </DashboardCard>

      {/* 실제 Slack으로 발송된 payload 원문 그대로 — 가공하지 않는다. */}
      <DashboardCard title="발송 메시지 원문">
        <pre className="whitespace-pre-wrap break-words rounded-md bg-body p-4 text-sm text-neutral-300">
          {alert.message}
        </pre>
      </DashboardCard>
    </div>
  )
}

function AlertTimeline({ alert }: { alert: Alert }) {
  const succeeded = alert.status === 'SENT'
  const failed = alert.status === 'FAILED'

  const steps = [
    { label: '이벤트 발생', detail: new Date(alert.createdAt).toLocaleString('ko-KR'), done: true },
    {
      label: '발송 시도',
      detail: alert.retryCount > 0 ? `재시도 ${alert.retryCount}회` : '1회 시도',
      done: true,
    },
    {
      label: succeeded ? '발송 성공' : failed ? '발송 실패' : '발송 대기 중',
      detail: succeeded ? '' : failed ? '재시도 소진' : '',
      done: succeeded || failed,
      failed,
    },
  ]

  return (
    <ol className="space-y-4">
      {steps.map((step, i) => {
        const Icon = step.failed ? XCircle : step.done ? CheckCircle2 : Circle
        const tone = step.failed ? 'text-danger' : step.done ? 'text-primary' : 'text-neutral-600'
        return (
          <li key={i} className="flex items-start gap-3">
            <Icon size={18} className={`mt-0.5 shrink-0 ${tone}`} />
            <div>
              <div className="text-sm text-neutral-200">{step.label}</div>
              {step.detail && <div className="text-xs text-neutral-500">{step.detail}</div>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
