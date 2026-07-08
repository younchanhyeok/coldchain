export type Tab = 'dashboard' | 'cargo' | 'alerts'

// 화면_탭_구성.md 확정 순서: 대시보드 → 화물 관리 → 알림 → 리포트 → 배송 현황.
// 위험 모니터링은 "M3엔 사이드바 미노출"이 확정 사항이라 M4 전까지 목록에서 아예 뺀다.
// 설정 탭은 채울 실체가 없어 삭제 확정.
const NAV_ITEMS: { label: string; tab: Tab | null }[] = [
  { label: '대시보드', tab: 'dashboard' },
  { label: '화물 관리', tab: 'cargo' },
  { label: '알림', tab: 'alerts' },
  { label: '리포트', tab: null }, // M4
  { label: '배송 현황', tab: null }, // PR6에서 구현
]

interface SidebarProps {
  activeTab: Tab
  onSelectTab: (tab: Tab) => void
}

export function Sidebar({ activeTab, onSelectTab }: SidebarProps) {
  return (
    <aside className="flex w-[260px] shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="px-4 py-5 text-lg font-semibold tracking-tight text-neutral-100">
        ❄ ColdChain
      </div>
      <nav className="flex-1 px-2">
        <ul className="space-y-1">
          {NAV_ITEMS.map((item) =>
            item.tab ? (
              <li key={item.label}>
                <button
                  type="button"
                  onClick={() => onSelectTab(item.tab!)}
                  className={`block w-full rounded-md px-3 py-2 text-left text-sm ${
                    activeTab === item.tab ? 'bg-primary/15 text-primary' : 'text-neutral-400 hover:bg-card-hover'
                  }`}
                >
                  {item.label}
                </button>
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
