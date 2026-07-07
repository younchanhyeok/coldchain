import type { ReactNode } from 'react'

interface DashboardCardProps {
  title: string
  meta?: string
  status?: ReactNode
  footer?: ReactNode
  bodyClassName?: string
  noHover?: boolean
  children: ReactNode
}

/**
 * 모든 주요 시각화(지도/차트/테이블/타임라인)는 이 카드로 감싼다 — 원본 콘텐츠가
 * 페이지에 그대로 놓이지 않게 한다(.claude/skills/ui-styling의 Card 규칙).
 *
 * flex-col + h-full 구조라 부모가 그리드 등으로 카드 높이를 정해주면(예: h-[480px])
 * 헤더/푸터는 고정되고 바디만 유동(overflow-y-auto 등)한다.
 */
export function DashboardCard({ title, meta, status, footer, bodyClassName, noHover, children }: DashboardCardProps) {
  return (
    <div
      className={`flex h-full flex-col overflow-hidden rounded-card border border-border bg-card shadow-card ${
        noHover ? '' : 'transition-all duration-[250ms] ease-out hover:border-[#333333] hover:shadow-card-hover'
      }`}
    >
      <div className="flex shrink-0 items-center justify-between px-7 pt-6 pb-4">
        <div className="flex items-baseline gap-2">
          <h2 className="text-sm font-semibold text-neutral-200">{title}</h2>
          {meta && <span className="text-xs text-neutral-500">{meta}</span>}
        </div>
        {status}
      </div>

      <div className={`min-h-0 flex-1 ${bodyClassName ?? 'px-7 pb-7'}`}>{children}</div>

      {footer && <div className="shrink-0 border-t border-divider px-7 py-3">{footer}</div>}
    </div>
  )
}
