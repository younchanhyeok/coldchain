export type CargoStatusFilter = '' | 'SAFE' | 'CAUTION' | 'BREACH' | 'DELIVERED'

interface CargoSearchBarProps {
  keyword: string
  onKeywordChange: (value: string) => void
  statusFilter: CargoStatusFilter
  onStatusFilterChange: (value: CargoStatusFilter) => void
}

// 화면_탭_구성.md: 검색·필터는 프론트 클라이언트 사이드 — 백엔드에 쿼리 안 나간다.
export function CargoSearchBar({
  keyword,
  onKeywordChange,
  statusFilter,
  onStatusFilterChange,
}: CargoSearchBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <input
        type="text"
        placeholder="화물 ID 검색"
        value={keyword}
        onChange={(e) => onKeywordChange(e.target.value)}
        className="h-11 flex-1 min-w-[200px] rounded-control border border-border bg-card px-3 text-sm text-neutral-200 placeholder:text-neutral-600"
      />
      <select
        className="h-11 rounded-control border border-border bg-card px-3 text-sm text-neutral-200"
        value={statusFilter}
        onChange={(e) => onStatusFilterChange(e.target.value as CargoStatusFilter)}
      >
        <option value="">전체 상태</option>
        <option value="SAFE">정상</option>
        <option value="CAUTION">주의</option>
        <option value="BREACH">이탈</option>
        <option value="DELIVERED">완료</option>
      </select>
    </div>
  )
}
