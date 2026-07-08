import { CustomOverlayMap, Map, MapMarker, Polyline, useKakaoLoader } from 'react-kakao-maps-sdk'
import { getTrack } from '../../api/track'
import { usePolling } from '../../hooks/usePolling'
import type { GeoJsonLineString } from '../../types/track'
import type { TrackerStatus, TrackerSummary } from '../../types/tracker'
import { DashboardCard } from '../dashboard/DashboardCard'

const KAKAO_MAP_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

const STATUS_COLOR: Record<TrackerStatus, string> = {
  SAFE: '#5ea9ff',
  CAUTION: '#f5a623',
  RISK: '#f5a623',
  BREACH: '#ff6b6b',
}

interface LiveOpsMapProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

function toLatLng(line: GeoJsonLineString) {
  return line.coordinates.map(([lon, lat]) => ({ lat, lng: lon }))
}

function LegendDot({ colorHex, label }: { colorHex: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-2 w-2 rounded-full" style={{ background: colorHex }} />
      {label}
    </span>
  )
}

export function LiveOpsMap({ trackers, selectedTrackerId, onSelectTracker }: LiveOpsMapProps) {
  const [loading, error] = useKakaoLoader({ appkey: KAKAO_MAP_KEY ?? '' })

  const { data: track } = usePolling(
    () => (selectedTrackerId ? getTrack(selectedTrackerId) : Promise.resolve(null)),
    30_000,
    [selectedTrackerId],
  )

  const positioned = trackers.filter((t) => t.lastPosition != null)
  const safeCount = trackers.filter((t) => t.status === 'SAFE').length
  const breachCount = trackers.filter((t) => t.status === 'BREACH').length

  const footer = (
    <div className="flex items-center gap-4 text-xs text-neutral-400">
      <LegendDot colorHex={STATUS_COLOR.SAFE} label={`정상 ${safeCount}`} />
      <LegendDot colorHex={STATUS_COLOR.BREACH} label={`이탈 ${breachCount}`} />
    </div>
  )

  function renderBody() {
    if (!KAKAO_MAP_KEY) {
      return <PlaceholderBody message="지도 API 키가 설정되지 않았습니다 (VITE_KAKAO_MAP_KEY)" />
    }
    if (loading) {
      return <PlaceholderBody message="지도를 불러오는 중..." />
    }
    if (error) {
      return <PlaceholderBody message="지도를 불러오지 못했습니다." />
    }
    if (positioned.length === 0) {
      return <PlaceholderBody message="위치 데이터가 있는 트래커가 없습니다." />
    }

    const center = { lat: positioned[0].lastPosition!.lat, lng: positioned[0].lastPosition!.lon }
    const pathLatLng = track ? toLatLng(track.path) : []

    return (
      <Map center={center} level={9} style={{ width: '100%', height: '100%', minHeight: 420 }}>
        {positioned.map((t) => (
          <CustomOverlayMap
            key={t.trackerId}
            position={{ lat: t.lastPosition!.lat, lng: t.lastPosition!.lon }}
            zIndex={t.trackerId === selectedTrackerId ? 10 : 1}
          >
            <button
              type="button"
              onClick={() => onSelectTracker(t.trackerId)}
              style={{
                width: t.trackerId === selectedTrackerId ? 18 : 12,
                height: t.trackerId === selectedTrackerId ? 18 : 12,
                borderRadius: 9999,
                background: STATUS_COLOR[t.status],
                border: '2px solid white',
                boxShadow:
                  t.trackerId === selectedTrackerId
                    ? `0 0 0 5px ${STATUS_COLOR[t.status]}40`
                    : '0 1px 3px rgba(0,0,0,0.4)',
                cursor: 'pointer',
                padding: 0,
              }}
              title={t.trackerId}
            />
          </CustomOverlayMap>
        ))}

        {selectedTrackerId && track && (
          <>
            {pathLatLng.length > 1 && (
              <Polyline
                path={pathLatLng}
                strokeWeight={3}
                strokeColor="#5ea9ff"
                strokeOpacity={0.9}
                strokeStyle="solid"
              />
            )}
            {track.current && (
              <Polyline
                path={[
                  { lat: track.current.lat, lng: track.current.lon },
                  { lat: track.destination.lat, lng: track.destination.lon },
                ]}
                strokeWeight={2}
                strokeColor="#5ea9ff"
                strokeOpacity={0.5}
                strokeStyle="dash"
              />
            )}
            <MapMarker position={{ lat: track.destination.lat, lng: track.destination.lon }} />
            <CustomOverlayMap position={{ lat: track.destination.lat, lng: track.destination.lon }} yAnchor={2.2}>
              <div className="rounded-md border border-border bg-card px-2 py-1 text-xs whitespace-nowrap text-neutral-100 shadow-card">
                {track.destination.name}
                {track.etaMinutes != null && ` · 도착 예상 ${Math.round(track.etaMinutes)}분`}
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
          </>
        )}
      </Map>
    )
  }

  return (
    <DashboardCard
      title="실시간 전체 지도"
      meta={`추적 ${positioned.length}건`}
      footer={footer}
      bodyClassName="p-0"
      noHover
    >
      {renderBody()}
    </DashboardCard>
  )
}

function PlaceholderBody({ message }: { message: string }) {
  return <div className="flex min-h-[420px] items-center justify-center text-sm text-neutral-500">{message}</div>
}
