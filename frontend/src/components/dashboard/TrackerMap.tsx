import { CustomOverlayMap, Map, MapMarker, Polyline, useKakaoLoader } from 'react-kakao-maps-sdk'
import { getTrack } from '../../api/track'
import { usePolling } from '../../hooks/usePolling'

const KAKAO_MAP_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

interface TrackerMapProps {
  trackerId: string | null
  currentPosition: { lat: number; lon: number } | null
}

function Placeholder({ message }: { message: string }) {
  return (
    <div className="flex h-full min-h-[320px] items-center justify-center rounded-card border border-border bg-card text-sm text-neutral-500 shadow-card">
      {message}
    </div>
  )
}

export function TrackerMap({ trackerId, currentPosition }: TrackerMapProps) {
  const [loading, error] = useKakaoLoader({ appkey: KAKAO_MAP_KEY ?? '' })

  // 경로/목적지/breachPoints는 30초 폴링 — 현재 위치는 부모가 SSE로 받은 lastPosition을 쓴다(prop).
  const { data: track } = usePolling(
    () => (trackerId ? getTrack(trackerId) : Promise.resolve(null)),
    30_000,
    [trackerId],
  )

  if (!KAKAO_MAP_KEY) {
    return <Placeholder message="지도 API 키가 설정되지 않았습니다 (VITE_KAKAO_MAP_KEY)" />
  }
  if (!trackerId) {
    return <Placeholder message="트래커를 선택하세요." />
  }
  if (loading) {
    return <Placeholder message="지도를 불러오는 중..." />
  }
  if (error || !track) {
    return <Placeholder message="지도를 불러오지 못했습니다." />
  }

  const effectiveCurrent = currentPosition ?? track.current
  const pathLatLng = track.path.coordinates.map(([lon, lat]) => ({ lat, lng: lon }))
  const center = effectiveCurrent
    ? { lat: effectiveCurrent.lat, lng: effectiveCurrent.lon }
    : { lat: track.destination.lat, lng: track.destination.lon }

  return (
    <div className="overflow-hidden rounded-card border border-border shadow-card">
      <Map center={center} level={7} style={{ width: '100%', height: '100%', minHeight: 320 }}>
        {pathLatLng.length > 1 && (
          <Polyline path={pathLatLng} strokeWeight={3} strokeColor="#5ea9ff" strokeOpacity={0.9} strokeStyle="solid" />
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

        {track.breachPoints.map((point) => (
          <CustomOverlayMap key={`${point.lat}-${point.lon}-${point.ts}`} position={{ lat: point.lat, lng: point.lon }}>
            <div
              style={{
                width: 10,
                height: 10,
                borderRadius: 9999,
                background: '#ff6b6b',
                border: '2px solid white',
              }}
            />
          </CustomOverlayMap>
        ))}
      </Map>
    </div>
  )
}
