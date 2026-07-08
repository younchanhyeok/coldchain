import { useEffect, useMemo, useState } from 'react'
import { getShipments } from '../api/shipments'
import { getSummary } from '../api/summary'
import { CargoAlertHistory } from '../components/cargo/CargoAlertHistory'
import { CargoDetailPanel } from '../components/cargo/CargoDetailPanel'
import { CargoKpiTiles } from '../components/cargo/CargoKpiTiles'
import { CargoList } from '../components/cargo/CargoList'
import { CargoSearchBar, type CargoStatusFilter } from '../components/cargo/CargoSearchBar'
import { usePolling } from '../hooks/usePolling'
import type { ShipmentSummary } from '../types/shipment'

function matchesStatusFilter(shipment: ShipmentSummary, filter: CargoStatusFilter): boolean {
  if (!filter) return true
  if (filter === 'DELIVERED') return shipment.shipmentStatus === 'DELIVERED'
  return shipment.shipmentStatus !== 'DELIVERED' && shipment.trackerStatus === filter
}

function pickDefaultTrackerId(shipments: ShipmentSummary[]): string | null {
  if (shipments.length === 0) return null
  const breached = shipments.find((s) => s.shipmentStatus !== 'DELIVERED' && s.trackerStatus === 'BREACH')
  return (breached ?? shipments[0]).trackerId
}

export function CargoManagementPage() {
  const { data: summary } = usePolling(() => getSummary(), 30_000)
  const { data: shipmentList, loading, error } = usePolling(() => getShipments({ size: 200 }), 30_000)

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<CargoStatusFilter>('')
  const [selectedTrackerId, setSelectedTrackerId] = useState<string | null>(null)

  const shipments = useMemo(() => shipmentList?.content ?? [], [shipmentList])

  const filteredShipments = useMemo(
    () =>
      shipments.filter(
        (s) =>
          (keyword.trim() === '' || s.trackerId.toLowerCase().includes(keyword.trim().toLowerCase())) &&
          matchesStatusFilter(s, statusFilter),
      ),
    [shipments, keyword, statusFilter],
  )

  useEffect(() => {
    setSelectedTrackerId((current) => {
      if (current && filteredShipments.some((s) => s.trackerId === current)) return current
      return pickDefaultTrackerId(filteredShipments)
    })
  }, [filteredShipments])

  if (loading && shipments.length === 0) {
    return <div className="text-neutral-500">불러오는 중...</div>
  }

  if (error) {
    return <div className="text-danger">데이터를 불러오지 못했습니다: {error.message}</div>
  }

  return (
    <div className="flex flex-col gap-6">
      <CargoKpiTiles summary={summary} />
      <CargoSearchBar
        keyword={keyword}
        onKeywordChange={setKeyword}
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
      />
      {/* 오른쪽(상태개요+배송정보+온도추이+AI예측)과 왼쪽(목록+알림 이력)의 높이를 서로 맞추려는
          시도(items-stretch/JS 측정 등)를 모두 버림 — auto 트랙은 한 행 안 모든 아이템의
          max-content로 계속 자라기 때문에 한쪽만 배제할 방법이 없다(overflow-hidden도 안 됨).
          대신 두 컬럼을 완전히 독립시킨다: 오른쪽은 items-start로 그리드 stretch를 꺼 자연
          높이 그대로, 왼쪽은 카드마다 자체 고정 높이 + 내부 overflow-y-auto로 스스로 줄인다. */}
      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="flex flex-col gap-6">
          <div className="h-[560px]">
            <CargoList
              shipments={filteredShipments}
              selectedTrackerId={selectedTrackerId}
              onSelectTracker={setSelectedTrackerId}
            />
          </div>
          <div className="h-[280px]">
            <CargoAlertHistory trackerId={selectedTrackerId} />
          </div>
        </div>
        {/* 왼쪽 목록을 스크롤해도 선택한 화물의 상세 패널은 화면에 고정 — main(overflow-auto)
            기준 sticky. */}
        <div className="sticky top-6">
          <CargoDetailPanel trackerId={selectedTrackerId} />
        </div>
      </div>
    </div>
  )
}
