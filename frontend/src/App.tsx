import { useState } from 'react'
import { getTrackers } from './api/trackers'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { StatusFilter } from './components/layout/TopBar'
import { usePolling } from './hooks/usePolling'
import { DashboardPage } from './pages/DashboardPage'

function App() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  const { data, error, loading, updatedAt } = usePolling(
    () => getTrackers({ status: statusFilter || undefined, size: 100 }),
    30_000,
    [statusFilter],
  )

  return (
    <DashboardLayout statusFilter={statusFilter} onStatusFilterChange={setStatusFilter} lastUpdated={updatedAt}>
      <DashboardPage trackers={data?.content ?? []} loading={loading} error={error} />
    </DashboardLayout>
  )
}

export default App
