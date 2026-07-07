import { useState } from 'react'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { StatusFilter } from './components/layout/TopBar'
import { useTrackerStream } from './hooks/useTrackerStream'
import { DashboardPage } from './pages/DashboardPage'

function App() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  const { trackers, error, loading, updatedAt } = useTrackerStream(statusFilter)

  return (
    <DashboardLayout statusFilter={statusFilter} onStatusFilterChange={setStatusFilter} lastUpdated={updatedAt}>
      <DashboardPage trackers={trackers} loading={loading} error={error} />
    </DashboardLayout>
  )
}

export default App
