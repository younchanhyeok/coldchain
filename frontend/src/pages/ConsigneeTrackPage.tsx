import { useParams } from 'react-router-dom'
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Map, MapMarker, useKakaoLoader } from 'react-kakao-maps-sdk'
import { ApiError } from '../api/client'
import { getConsigneeTrack } from '../api/consigneeTrack'
import { usePolling } from '../hooks/usePolling'
import type { ConsigneeTrackResponse } from '../types/consigneeTrack'

const KAKAO_MAP_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

const SHIPMENT_STATUS_LABEL: Record<ConsigneeTrackResponse['shipment']['status'], string> = {
  READY: '배송 준비중',
  IN_TRANSIT: '배송 중',
  DELIVERED: '배송 완료',
}

const TEMPERATURE_STATUS_STYLE: Record<ConsigneeTrackResponse['temperatureStatus'], string> = {
  SAFE: 'bg-success/15 text-success',
  BREACH: 'bg-danger/15 text-danger',
  UNKNOWN: 'bg-neutral-700/30 text-neutral-400',
}

const TEMPERATURE_STATUS_LABEL: Record<ConsigneeTrackResponse['temperatureStatus'], string> = {
  SAFE: '정상',
  BREACH: '온도 이탈',
  UNKNOWN: '데이터 없음',
}

function formatDistance(meters: number | null): string {
  if (meters == null) return '-'
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${Math.round(meters)}m`
}

function formatEta(iso: string | null): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatTs(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function CenteredMessage({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-body px-6 text-center text-neutral-300">
      {children}
    </div>
  )
}

export function ConsigneeTrackPage() {
  const { token } = useParams<{ token: string }>()

  const { data, error, loading } = usePolling(
    () => (token ? getConsigneeTrack(token) : Promise.reject(new Error('토큰이 없습니다.'))),
    30_000,
    [token],
  )

  if (error instanceof ApiError && (error.status === 401 || error.code === 'MAGIC_LINK_EXPIRED')) {
    return <CenteredMessage>이 링크는 만료되었습니다. 화주에게 새 링크를 요청해 주세요.</CenteredMessage>
  }
  if (error instanceof ApiError && error.status === 404) {
    return <CenteredMessage>유효하지 않은 링크입니다.</CenteredMessage>
  }
  if (error) {
    return <CenteredMessage>배송 정보를 불러오지 못했습니다.</CenteredMessage>
  }
  if (loading && !data) {
    return <CenteredMessage>불러오는 중...</CenteredMessage>
  }
  if (!data) {
    return null
  }

  return (
    <div className="min-h-screen bg-body px-4 py-6 text-neutral-100">
      <div className="mx-auto flex max-w-md flex-col gap-4">
        <div>
          <div className="text-xs text-neutral-500">{data.shipment.shipperName}</div>
          <h1 className="text-lg font-semibold tracking-tight">{data.shipment.productName}</h1>
        </div>

        <div className="flex items-center gap-2">
          <span className="rounded-md bg-card-hover px-2 py-1 text-xs text-neutral-300">
            {SHIPMENT_STATUS_LABEL[data.shipment.status]}
          </span>
          <span className={`rounded-md px-2 py-1 text-xs ${TEMPERATURE_STATUS_STYLE[data.temperatureStatus]}`}>
            {TEMPERATURE_STATUS_LABEL[data.temperatureStatus]}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-card border border-border bg-card p-4 shadow-card">
            <div className="text-xs text-neutral-500">현재 온도</div>
            <div className="mt-1 text-2xl font-semibold">
              {data.currentTemperature != null ? `${data.currentTemperature}℃` : '-'}
            </div>
            <div className="mt-1 text-xs text-neutral-500">임계 {data.thresholdTemp}℃</div>
          </div>
          <div className="rounded-card border border-border bg-card p-4 shadow-card">
            <div className="text-xs text-neutral-500">도착 예정</div>
            <div className="mt-1 text-base font-semibold">{formatEta(data.shipment.eta)}</div>
            <div className="mt-1 text-xs text-neutral-500">남은 거리 {formatDistance(data.remainingDistanceMeters)}</div>
          </div>
        </div>

        {data.position && (
          <div className="overflow-hidden rounded-card border border-border shadow-card">
            {KAKAO_MAP_KEY ? (
              <ConsigneeMap position={data.position} />
            ) : (
              <div className="flex h-[220px] items-center justify-center bg-card text-sm text-neutral-500">
                지도 API 키가 설정되지 않았습니다.
              </div>
            )}
          </div>
        )}

        <div className="rounded-card border border-border bg-card p-4 shadow-card">
          <div className="mb-2 text-xs text-neutral-500">온도 추이</div>
          {data.temperatureLog.length === 0 ? (
            <p className="text-sm text-neutral-500">아직 온도 기록이 없습니다.</p>
          ) : (
            <ResponsiveContainer width="100%" height={160}>
              <LineChart data={data.temperatureLog.map((p) => ({ ts: formatTs(p.ts), temperature: p.temperature }))}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1d1d1d" />
                <XAxis dataKey="ts" stroke="#737373" fontSize={11} />
                <YAxis stroke="#737373" fontSize={11} unit="℃" width={36} />
                <Tooltip
                  contentStyle={{ background: '#18181b', border: '1px solid #262626', fontSize: 12 }}
                  labelStyle={{ color: '#a3a3a3' }}
                />
                <ReferenceLine
                  y={data.thresholdTemp}
                  stroke="#ff6b6b"
                  strokeDasharray="4 4"
                  label={{ value: `임계 ${data.thresholdTemp}℃`, fill: '#ff6b6b', fontSize: 10, position: 'insideTopRight' }}
                />
                <Line type="monotone" dataKey="temperature" stroke="#5ea9ff" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <p className="text-center text-xs text-neutral-600">30초마다 자동 갱신됩니다.</p>
      </div>
    </div>
  )
}

function ConsigneeMap({ position }: { position: { lat: number; lon: number } }) {
  const [loading, error] = useKakaoLoader({ appkey: KAKAO_MAP_KEY ?? '' })

  if (loading || error) {
    return (
      <div className="flex h-[220px] items-center justify-center bg-card text-sm text-neutral-500">
        지도를 불러오는 중...
      </div>
    )
  }

  const center = { lat: position.lat, lng: position.lon }
  return (
    <Map center={center} level={6} style={{ width: '100%', height: 220 }}>
      <MapMarker position={center} />
    </Map>
  )
}
