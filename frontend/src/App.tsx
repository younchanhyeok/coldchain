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

  // statusFilter 드롭다운은 대시보드·배송 현황에만 보이지만, trackers는 앱 전체가 공유하는
  // useTrackerStream 하나에서 나온다 — 탭을 옮길 때 필터를 리셋하지 않으면 예: "이탈 위험만"을
  // 걸어둔 채 위험 모니터링 탭으로 와도 그 필터가 그대로 적용돼, RISK 상태(예측 위험) 트래커가
  // BREACH가 아니라서 서버 필터에 걸러지고 위험 우선순위 리스트가 활성 예측을 놓친다.
  function handleSelectTab(tab: Tab) {
    setActiveTab(tab)
    if (tab !== 'dashboard' && tab !== 'liveops') {
      setStatusFilter('')
    }
  }

  return (
    <DashboardLayout
      title={TAB_TITLES[activeTab]}
      activeTab={activeTab}
      onSelectTab={handleSelectTab}
      statusFilter={activeTab === 'dashboard' || activeTab === 'liveops' ? statusFilter : undefined}
      onStatusFilterChange={activeTab === 'dashboard' || activeTab === 'liveops' ? setStatusFilter : undefined}
      lastUpdated={updatedAt}
    >
      {activeTab === 'dashboard' ? (
        <DashboardPage
          trackers={trackers}
          loading={loading}
          error={error}
          onNavigateToCargo={() => handleSelectTab('cargo')}
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
