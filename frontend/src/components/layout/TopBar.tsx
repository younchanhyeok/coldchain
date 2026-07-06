export type StatusFilter = '' | 'SAFE' | 'BREACH'

interface TopBarProps {
  statusFilter: StatusFilter
  onStatusFilterChange: (value: StatusFilter) => void
}

export function TopBar({ statusFilter, onStatusFilterChange }: TopBarProps) {
  return (
    <header className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
      <h1 className="text-lg font-semibold text-slate-100">화주 대시보드</h1>
      <div className="flex items-center gap-3">
        <span
          className="cursor-default select-none rounded-md border border-slate-800 px-3 py-1.5 text-sm text-slate-600"
          title="기간별 조회는 아직 지원하지 않습니다"
        >
          날짜 범위 (준비 중)
        </span>
        <select
          className="rounded-md border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm text-slate-200"
          value={statusFilter}
          onChange={(e) => onStatusFilterChange(e.target.value as StatusFilter)}
        >
          <option value="">전체 화물</option>
          <option value="SAFE">정상만</option>
          <option value="BREACH">이탈 위험만</option>
        </select>
        <span className="cursor-default select-none text-slate-600" title="준비 중">
          🔔
        </span>
        <span className="cursor-default select-none text-slate-600" title="준비 중">
          ⚙️
        </span>
      </div>
    </header>
  )
}
