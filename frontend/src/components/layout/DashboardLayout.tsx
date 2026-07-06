import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { TopBar, type StatusFilter } from './TopBar'

interface DashboardLayoutProps {
  statusFilter: StatusFilter
  onStatusFilterChange: (value: StatusFilter) => void
  lastUpdated: Date | null
  children: ReactNode
}

export function DashboardLayout({
  statusFilter,
  onStatusFilterChange,
  lastUpdated,
  children,
}: DashboardLayoutProps) {
  return (
    <div className="flex min-h-screen bg-body text-neutral-100">
      <Sidebar />
      <div className="flex flex-1 flex-col">
        <TopBar statusFilter={statusFilter} onStatusFilterChange={onStatusFilterChange} lastUpdated={lastUpdated} />
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  )
}
