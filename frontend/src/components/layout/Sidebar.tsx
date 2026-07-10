import { useNavigate } from 'react-router-dom'
import { clearTokens, getCompanyName } from '../../lib/auth'

export type Tab = 'dashboard' | 'cargo' | 'alerts' | 'liveops' | 'risk' | 'report'

// 화면_탭_구성.md 확정 순서: 대시보드 → 화물 관리 → 알림 → 리포트 → 배송 현황 → 위험 모니터링.
// 위험 모니터링·리포트 둘 다 데이터 전부가 M4 예측 산물이라 M4에 한 번에 노출한다.
// 설정 탭은 채울 실체가 없어 삭제 확정.
const NAV_ITEMS: { label: string; tab: Tab }[] = [
  { label: '대시보드', tab: 'dashboard' },
  { label: '화물 관리', tab: 'cargo' },
  { label: '알림', tab: 'alerts' },
  { label: '리포트', tab: 'report' },
  { label: '배송 현황', tab: 'liveops' },
  { label: '위험 모니터링', tab: 'risk' },
]

interface SidebarProps {
  activeTab: Tab
  onSelectTab: (tab: Tab) => void
}

export function Sidebar({ activeTab, onSelectTab }: SidebarProps) {
  const navigate = useNavigate()

  function handleLogout() {
    clearTokens()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="flex w-[260px] shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="px-4 py-5 text-lg font-semibold tracking-tight text-neutral-100">
        ❄ ColdChain
      </div>
      <nav className="flex-1 px-2">
        <ul className="space-y-1">
          {NAV_ITEMS.map((item) => (
            <li key={item.label}>
              <button
                type="button"
                onClick={() => onSelectTab(item.tab)}
                className={`block w-full rounded-md px-3 py-2 text-left text-sm ${
                  activeTab === item.tab ? 'bg-primary/15 text-primary' : 'text-neutral-400 hover:bg-card-hover'
                }`}
              >
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      </nav>
      <div className="flex items-center justify-between border-t border-border px-4 py-3 text-xs text-neutral-500">
        <span>{getCompanyName() ?? '화주'}</span>
        <button type="button" onClick={handleLogout} className="text-neutral-500 hover:text-neutral-300">
          로그아웃
        </button>
      </div>
    </aside>
  )
}
