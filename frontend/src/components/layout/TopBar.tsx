import { useEffect, useState } from 'react'

export type StatusFilter = '' | 'SAFE' | 'BREACH'

interface TopBarProps {
  statusFilter: StatusFilter
  onStatusFilterChange: (value: StatusFilter) => void
  lastUpdated: Date | null
}

function useElapsedLabel(since: Date | null): string {
  const [, tick] = useState(0)

  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 15_000)
    return () => clearInterval(id)
  }, [])

  if (!since) return '업데이트 대기 중'
  const seconds = Math.floor((Date.now() - since.getTime()) / 1000)
  if (seconds < 60) return '방금 업데이트'
  return `${Math.floor(seconds / 60)}분 전 업데이트`
}

export function TopBar({ statusFilter, onStatusFilterChange, lastUpdated }: TopBarProps) {
  const elapsedLabel = useElapsedLabel(lastUpdated)

  return (
    <header className="flex h-[72px] items-center justify-between border-b border-border bg-header px-8">
      <h1 className="text-lg font-semibold text-neutral-100">화주 대시보드</h1>
      <div className="flex items-center gap-4">
        <span className="text-xs text-neutral-500">{elapsedLabel}</span>
        <span
          className="flex h-11 cursor-default items-center rounded-control border border-border px-3 text-sm text-neutral-600 select-none"
          title="기간별 조회는 아직 지원하지 않습니다"
        >
          날짜 범위 (준비 중)
        </span>
        <select
          className="h-11 rounded-control border border-border bg-card px-3 text-sm text-neutral-200"
          value={statusFilter}
          onChange={(e) => onStatusFilterChange(e.target.value as StatusFilter)}
        >
          <option value="">전체 화물</option>
          <option value="SAFE">정상만</option>
          <option value="BREACH">이탈 위험만</option>
        </select>
        <span className="cursor-default select-none text-neutral-600" title="준비 중">
          🔔
        </span>
        <span className="cursor-default select-none text-neutral-600" title="준비 중">
          ⚙️
        </span>
      </div>
    </header>
  )
}
