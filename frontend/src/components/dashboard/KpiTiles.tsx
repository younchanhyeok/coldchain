import { Package, TrendingUp, TriangleAlert, Truck } from 'lucide-react'
import type { ComponentType } from 'react'
import type { TrackerSummary } from '../../types/tracker'

interface KpiTilesProps {
  trackers: TrackerSummary[]
}

type Tone = 'primary' | 'warning' | 'success' | 'danger'

const TONE_TEXT: Record<Tone, string> = {
  primary: 'text-primary',
  warning: 'text-warning',
  success: 'text-success',
  danger: 'text-danger',
}

const TONE_BADGE: Record<Tone, string> = {
  primary: 'bg-primary/[0.18] border-primary/[0.08]',
  warning: 'bg-warning/[0.18] border-warning/[0.08]',
  success: 'bg-success/[0.18] border-success/[0.08]',
  danger: 'bg-danger/[0.18] border-danger/[0.08]',
}

export function KpiTiles({ trackers }: KpiTilesProps) {
  const inTransitCount = trackers.length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  return (
    <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
      <Tile
        icon={Truck}
        tone="primary"
        label="배송 중"
        value={String(inTransitCount)}
        sub="조회된 트래커 수"
        hasValue
      />
      <Tile
        icon={TriangleAlert}
        tone="warning"
        label="이탈 위험"
        value={String(breachCount)}
        sub="임계 온도 초과"
        hasValue
      />
      <Tile icon={Package} tone="success" label="구조된 박스" value="—" sub="M4 예측 이후 제공" hasValue={false} />
      <Tile icon={TrendingUp} tone="danger" label="이탈률" value="—" sub="M4 예측 이후 제공" hasValue={false} />
    </div>
  )
}

function Tile({
  icon: Icon,
  tone,
  label,
  value,
  sub,
  hasValue,
}: {
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  tone: Tone
  label: string
  value: string
  sub: string
  hasValue: boolean
}) {
  return (
    <div
      className="min-h-[180px] rounded-card border border-border bg-card p-7 shadow-card transition-all
        duration-[250ms] ease-out hover:-translate-y-0.5 hover:scale-[1.01] hover:border-[#333333]
        hover:bg-card-hover hover:shadow-card-hover"
    >
      <div className="text-sm text-neutral-400">{label}</div>
      <div className="mt-6 flex items-end justify-between gap-3">
        <div
          className={`text-[56px] leading-none font-semibold tracking-[-2px] ${
            hasValue ? 'text-neutral-50' : 'text-neutral-600'
          }`}
        >
          {value}
        </div>
        <div
          className={`flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full border backdrop-blur-[8px] ${TONE_BADGE[tone]}`}
        >
          <Icon size={30} strokeWidth={1.75} className={TONE_TEXT[tone]} />
        </div>
      </div>
      <div className="mt-3 text-xs text-neutral-500">{sub}</div>
    </div>
  )
}
