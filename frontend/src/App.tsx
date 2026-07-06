import { useState } from 'react'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { StatusFilter } from './components/layout/TopBar'
import { DashboardPage } from './pages/DashboardPage'

function App() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  return (
    <DashboardLayout statusFilter={statusFilter} onStatusFilterChange={setStatusFilter}>
      <DashboardPage statusFilter={statusFilter} />
    </DashboardLayout>
  )
}

export default App
