import { useState } from 'react'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { Tab } from './components/layout/Sidebar'
import type { StatusFilter } from './components/layout/TopBar'
import { useTrackerStream } from './hooks/useTrackerStream'
import { AlertsPage } from './pages/AlertsPage'
import { CargoManagementPage } from './pages/CargoManagementPage'
import { DashboardPage } from './pages/DashboardPage'
import { LiveOpsMapPage } from './pages/LiveOpsMapPage'

const TAB_TITLES: Record<Tab, string> = {
  dashboard: '화주 대시보드',
  cargo: '화물 관리',
  alerts: '알림 센터',
  liveops: '배송 현황',
}

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  const { trackers, error, loading, updatedAt } = useTrackerStream(statusFilter)

  return (
    <DashboardLayout
      title={TAB_TITLES[activeTab]}
      activeTab={activeTab}
      onSelectTab={setActiveTab}
      statusFilter={activeTab === 'dashboard' || activeTab === 'liveops' ? statusFilter : undefined}
      onStatusFilterChange={activeTab === 'dashboard' || activeTab === 'liveops' ? setStatusFilter : undefined}
      lastUpdated={updatedAt}
    >
      {activeTab === 'dashboard' ? (
        <DashboardPage
          trackers={trackers}
          loading={loading}
          error={error}
          onNavigateToCargo={() => setActiveTab('cargo')}
        />
      ) : activeTab === 'cargo' ? (
        <CargoManagementPage />
      ) : activeTab === 'alerts' ? (
        <AlertsPage />
      ) : (
        <LiveOpsMapPage trackers={trackers} loading={loading} error={error} />
      )}
    </DashboardLayout>
  )
}

export default App
