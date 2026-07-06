const NAV_ITEMS = [
  { label: '대시보드', active: true },
  { label: '화물 관리', active: false },
  { label: '위험 모니터링', active: false },
  { label: '배송 현황', active: false },
  { label: '알림', active: false },
  { label: '리포트', active: false },
  { label: '설정', active: false },
]

export function Sidebar() {
  return (
    <aside className="flex w-[260px] shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="px-4 py-5 text-lg font-semibold tracking-tight text-neutral-100">
        ❄ ColdChain
      </div>
      <nav className="flex-1 px-2">
        <ul className="space-y-1">
          {NAV_ITEMS.map((item) =>
            item.active ? (
              <li key={item.label}>
                <span className="block rounded-md bg-primary/15 px-3 py-2 text-sm text-primary">
                  {item.label}
                </span>
              </li>
            ) : (
              <li key={item.label}>
                {/* 아직 만들어지지 않은 화면 — 클릭 동작 자체가 없다(버튼/링크가 아닌 span) */}
                <span
                  className="block cursor-default select-none rounded-md px-3 py-2 text-sm text-neutral-600"
                  title="아직 준비되지 않은 화면입니다"
                >
                  {item.label}
                </span>
              </li>
            ),
          )}
        </ul>
      </nav>
      <div className="border-t border-border px-4 py-3 text-xs text-neutral-500">개발용 화주</div>
    </aside>
  )
}
