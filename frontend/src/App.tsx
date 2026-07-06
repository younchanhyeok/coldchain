import { useState } from 'react'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { StatusFilter } from './components/layout/TopBar'

function App() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  return (
    <DashboardLayout statusFilter={statusFilter} onStatusFilterChange={setStatusFilter}>
      <div className="text-slate-500">대시보드 콘텐츠 준비 중</div>
    </DashboardLayout>
  )
}

export default App
