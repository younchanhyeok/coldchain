import { useEffect, useState } from 'react'
import { CustomOverlayMap, Map, MapMarker, Polyline, useKakaoLoader } from 'react-kakao-maps-sdk'
import type { GeoJsonLineString } from '../../types/track'
import { getTrack } from '../../api/track'
import { usePolling } from '../../hooks/usePolling'
import type { TrackerSummary } from '../../types/tracker'
import { DashboardCard } from './DashboardCard'

const KAKAO_MAP_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

interface TrackerMapProps {
  trackerId: string | null
  currentPosition: { lat: number; lon: number } | null
  trackers: TrackerSummary[]
}

function LiveStatus() {
  return (
    <div className="flex items-center gap-1.5 text-xs text-neutral-500">
      <span className="h-2 w-2 rounded-full bg-success" />
      실시간
    </div>
  )
}

function LegendDot({ colorClassName, label }: { colorClassName: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`h-2 w-2 rounded-full ${colorClassName}`} />
      {label}
    </span>
  )
}

function toLatLng(line: GeoJsonLineString) {
  return line.coordinates.map(([lon, lat]) => ({ lat, lng: lon }))
}

export function TrackerMap({ trackerId, currentPosition, trackers }: TrackerMapProps) {
  const [loading, error] = useKakaoLoader({ appkey: KAKAO_MAP_KEY ?? '' })

  // 경로/목적지/breachSegments는 30초 폴링 — 현재 위치는 부모가 SSE로 받은 lastPosition을 쓴다(prop).
  const { data: track } = usePolling(
    () => (trackerId ? getTrack(trackerId) : Promise.resolve(null)),
    30_000,
    [trackerId],
  )

  const effectiveCurrent = currentPosition ?? track?.current ?? null

  // 센터는 트래커 선택 시 최초 1회만 고정 — 매 리딩마다 현재 위치로 재센터링하면 사용자가
  // 지도를 드래그해 다른 곳을 볼 수 없다(스냅백). 이후 이동은 사용자 조작에 맡긴다.
  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null)

  useEffect(() => {
    setCenter(null) // 선택 트래커가 바뀌면 새 트래커 기준으로 다시 센터를 잡는다
  }, [trackerId])

  useEffect(() => {
    if (center) return
    if (effectiveCurrent) {
      setCenter({ lat: effectiveCurrent.lat, lng: effectiveCurrent.lon })
    } else if (track) {
      setCenter({ lat: track.destination.lat, lng: track.destination.lon })
    }
  }, [center, effectiveCurrent, track])

  const safeCount = trackers.filter((t) => t.status === 'SAFE').length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  const footer = (
    <div className="flex items-center gap-4 text-xs text-neutral-400">
      <LegendDot colorClassName="bg-primary" label={`정상 ${safeCount}`} />
      <LegendDot colorClassName="bg-danger" label={`이탈 ${breachCount}`} />
    </div>
  )

  function renderBody() {
    if (!KAKAO_MAP_KEY) {
      return <PlaceholderBody message="지도 API 키가 설정되지 않았습니다 (VITE_KAKAO_MAP_KEY)" />
    }
    if (!trackerId) {
      return <PlaceholderBody message="트래커를 선택하세요." />
    }
    if (loading) {
      return <PlaceholderBody message="지도를 불러오는 중..." />
    }
    if (error || !track) {
      return <PlaceholderBody message="지도를 불러오지 못했습니다." />
    }
    if (!center) {
      return <PlaceholderBody message="지도를 불러오는 중..." />
    }

    const pathLatLng = toLatLng(track.path)

    return (
      <Map center={center} level={7} style={{ width: '100%', height: '100%', minHeight: 320 }}>
        {pathLatLng.length > 1 && (
          <Polyline path={pathLatLng} strokeWeight={3} strokeColor="#5ea9ff" strokeOpacity={0.9} strokeStyle="solid" />
        )}

        {effectiveCurrent && (
          <Polyline
            path={[
              { lat: effectiveCurrent.lat, lng: effectiveCurrent.lon },
              { lat: track.destination.lat, lng: track.destination.lon },
            ]}
            strokeWeight={2}
            strokeColor="#5ea9ff"
            strokeOpacity={0.5}
            strokeStyle="dash"
          />
        )}

        {effectiveCurrent && (
          <CustomOverlayMap position={{ lat: effectiveCurrent.lat, lng: effectiveCurrent.lon }}>
            <div
              style={{
                width: 14,
                height: 14,
                borderRadius: 9999,
                background: '#5ea9ff',
                border: '2px solid white',
                boxShadow: '0 0 0 4px rgba(94,169,255,0.25)',
              }}
            />
          </CustomOverlayMap>
        )}

        <MapMarker position={{ lat: track.destination.lat, lng: track.destination.lon }} />
        <CustomOverlayMap position={{ lat: track.destination.lat, lng: track.destination.lon }} yAnchor={2.2}>
          <div className="rounded-md border border-border bg-card px-2 py-1 text-xs whitespace-nowrap text-neutral-100 shadow-card">
            {track.destination.name}
          </div>
        </CustomOverlayMap>

        {track.breachSegments.map((segment, index) => (
          <Polyline
            key={index}
            path={toLatLng(segment)}
            strokeWeight={5}
            strokeColor="#ff6b6b"
            strokeOpacity={0.9}
            strokeStyle="solid"
          />
        ))}
      </Map>
    )
  }

  return (
    <DashboardCard
      title="배송 현황"
      meta={`현재 추적 ${trackers.length}건`}
      status={<LiveStatus />}
      footer={footer}
      bodyClassName="p-0"
      noHover
    >
      {renderBody()}
    </DashboardCard>
  )
}

function PlaceholderBody({ message }: { message: string }) {
  return (
    <div className="flex min-h-[320px] items-center justify-center text-sm text-neutral-500">{message}</div>
  )
}
