import { useState } from 'react'
import { DashboardLayout } from './components/layout/DashboardLayout'
import type { Tab } from './components/layout/Sidebar'
import type { StatusFilter } from './components/layout/TopBar'
import { useTrackerStream } from './hooks/useTrackerStream'
import { AlertsPage } from './pages/AlertsPage'
import { CargoManagementPage } from './pages/CargoManagementPage'
import { DashboardPage } from './pages/DashboardPage'
import { LiveOpsMapPage } from './pages/LiveOpsMapPage'
import { RiskMonitoringPage } from './pages/RiskMonitoringPage'

const TAB_TITLES: Record<Tab, string> = {
  dashboard: '화주 대시보드',
  cargo: '화물 관리',
  alerts: '알림 센터',
  liveops: '배송 현황',
  risk: '위험 모니터링',
}

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')

  const { trackers, error, loading, updatedAt, newAlertCount, resetNewAlertCount, lastPredictionAt } =
    useTrackerStream(statusFilter)

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
        <AlertsPage newAlertCount={newAlertCount} onDismissLiveBadge={resetNewAlertCount} />
      ) : activeTab === 'liveops' ? (
        <LiveOpsMapPage trackers={trackers} loading={loading} error={error} />
      ) : (
        <RiskMonitoringPage trackers={trackers} loading={loading} error={error} lastPredictionAt={lastPredictionAt} />
      )}
    </DashboardLayout>
  )
}

export default App
