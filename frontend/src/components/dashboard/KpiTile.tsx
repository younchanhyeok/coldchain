import type { ComponentType } from 'react'

export type KpiTone = 'primary' | 'warning' | 'success' | 'danger'

const TONE_TEXT: Record<KpiTone, string> = {
  primary: 'text-primary',
  warning: 'text-warning',
  success: 'text-success',
  danger: 'text-danger',
}

const TONE_BADGE: Record<KpiTone, string> = {
  primary: 'bg-primary/[0.18] border-primary/[0.08]',
  warning: 'bg-warning/[0.18] border-warning/[0.08]',
  success: 'bg-success/[0.18] border-success/[0.08]',
  danger: 'bg-danger/[0.18] border-danger/[0.08]',
}

interface KpiTileProps {
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  tone: KpiTone
  label: string
  value: string
  sub: string
  hasValue: boolean
}

// 대시보드/화물관리 등 여러 탭의 KPI 타일이 공유하는 시각 스타일 — 탭마다 데이터만 다르다.
export function KpiTile({ icon: Icon, tone, label, value, sub, hasValue }: KpiTileProps) {
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
