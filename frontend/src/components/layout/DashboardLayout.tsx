import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { TopBar, type StatusFilter } from './TopBar'

interface DashboardLayoutProps {
  statusFilter: StatusFilter
  onStatusFilterChange: (value: StatusFilter) => void
  children: ReactNode
}

export function DashboardLayout({ statusFilter, onStatusFilterChange, children }: DashboardLayoutProps) {
  return (
    <div className="flex min-h-screen bg-slate-950 text-slate-100">
      <Sidebar />
      <div className="flex flex-1 flex-col">
        <TopBar statusFilter={statusFilter} onStatusFilterChange={onStatusFilterChange} />
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  )
}
