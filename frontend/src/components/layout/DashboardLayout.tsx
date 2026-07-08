import type { ReactNode } from 'react'
import { Sidebar, type Tab } from './Sidebar'
import { TopBar, type StatusFilter } from './TopBar'

interface DashboardLayoutProps {
  title: string
  activeTab: Tab
  onSelectTab: (tab: Tab) => void
  statusFilter?: StatusFilter
  onStatusFilterChange?: (value: StatusFilter) => void
  lastUpdated: Date | null
  children: ReactNode
}

export function DashboardLayout({
  title,
  activeTab,
  onSelectTab,
  statusFilter,
  onStatusFilterChange,
  lastUpdated,
  children,
}: DashboardLayoutProps) {
  return (
    <div className="flex min-h-screen bg-body text-neutral-100">
      <Sidebar activeTab={activeTab} onSelectTab={onSelectTab} />
      <div className="flex flex-1 flex-col">
        <TopBar
          title={title}
          statusFilter={statusFilter}
          onStatusFilterChange={onStatusFilterChange}
          lastUpdated={lastUpdated}
        />
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  )
}
