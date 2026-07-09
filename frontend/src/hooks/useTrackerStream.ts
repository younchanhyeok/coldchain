import { useCallback, useEffect, useRef, useState } from 'react'
import { BASE_URL } from '../api/client'
import { getTrackers } from '../api/trackers'
import type { StatusFilter } from '../components/layout/TopBar'
import type { PredictionStreamEvent, ReadingStreamEvent } from '../types/stream'
import type { TrackerStatus, TrackerSummary } from '../types/tracker'
import { emitPredictionEvent } from './usePredictionRefreshSignal'

interface TrackerStreamResult {
  trackers: TrackerSummary[]
  error: Error | null
  loading: boolean
  updatedAt: Date | null
  /** SSE `alert` 이벤트 누적 건수 — 알림 탭 Live 배지용. */
  newAlertCount: number
  resetNewAlertCount: () => void
  /** 가장 최근 SSE `prediction` 이벤트 수신 시각 — 위험 모니터링 탭 "최근 분석 N초 전" 표시용. */
  lastPredictionAt: Date | null
}

/**
 * 초기 목록은 REST로 불러오고, 이후 갱신은 SSE(`/api/v1/stream`)의 reading 이벤트로 받는다.
 * 연결이 열릴 때마다(최초 연결 포함, 브라우저의 자동 재연결 포함) REST 전체 재조회로 리싱크한다
 * (API 명세 §5.1의 MVP 재연결 정책).
 *
 * 알림 Live 배지(`alert` 이벤트 카운트)도 여기서 함께 구독한다 — 탭마다 EventSource를 따로
 * 열면 클라이언트당 SSE 연결이 늘어나므로, 앱 전체가 이 훅의 연결 하나를 공유한다.
 */
export function useTrackerStream(statusFilter: StatusFilter): TrackerStreamResult {
  const [trackers, setTrackers] = useState<TrackerSummary[]>([])
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState(true)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const [newAlertCount, setNewAlertCount] = useState(0)
  const [lastPredictionAt, setLastPredictionAt] = useState<Date | null>(null)
  const statusFilterRef = useRef(statusFilter)
  statusFilterRef.current = statusFilter

  const resetNewAlertCount = useCallback(() => setNewAlertCount(0), [])

  useEffect(() => {
    let cancelled = false

    async function reload() {
      try {
        const res = await getTrackers({ status: statusFilterRef.current || undefined, size: 100 })
        if (!cancelled) {
          setTrackers(res.content)
          setError(null)
          setUpdatedAt(new Date())
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error(String(err)))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    reload()

    const source = new EventSource(`${BASE_URL}/api/v1/stream`)

    source.addEventListener('open', () => {
      reload()
    })

    source.addEventListener('reading', (e: MessageEvent<string>) => {
      const payload = JSON.parse(e.data) as ReadingStreamEvent
      let missing = false

      setTrackers((current) => {
        const next = current.map((t): TrackerSummary => {
          if (t.trackerId !== payload.trackerId) return t
          // reading 이벤트의 status는 서버가 리딩당 DB 조회 없이 계산한 값이라 BREACH/SAFE
          // 두 가지뿐이다 — 그대로 덮어쓰면 RISK(활성 예측)·CAUTION(활성 이상)이 다음 리딩에
          // SAFE로 지워진다. 그래서 BREACH 축의 전이만 반영한다: BREACH 진입은 즉시,
          // BREACH 해제는 일단 SAFE로(실제 RISK/CAUTION 여부는 다음 reload가 교정),
          // 그 외에는 기존 상태 유지. RISK/CAUTION 전이 자체는 prediction/anomaly 이벤트의
          // reload가 담당한다.
          const status: TrackerStatus =
            payload.status === 'BREACH' ? 'BREACH' : t.status === 'BREACH' ? payload.status : t.status
          return {
            ...t,
            lastTemperature: payload.temperature,
            lastPosition: { lat: payload.lat, lon: payload.lon },
            lastReportedAt: payload.ts,
            status,
          }
        })
        missing = !current.some((t) => t.trackerId === payload.trackerId)
        return next
      })

      if (missing) {
        reload() // 모르는 트래커면 목록에 새로 생겼을 수 있으니 전체 재조회
      }
      setUpdatedAt(new Date())
    })

    source.addEventListener('heartbeat', () => setUpdatedAt(new Date()))

    source.addEventListener('alert', () => setNewAlertCount((count) => count + 1))

    // prediction 이벤트는 에피소드 생성/취소/무효화/만료/적중 전이 시에만 오고(리딩마다가
    // 아님) 상태 판정(RISK 포함)과 activePrediction 필드가 함께 바뀌므로, reading 이벤트처럼
    // 부분 patch 대신 전체 재조회로 정확한 최신 상태를 받는다 — 빈도가 낮아 비용도 작다.
    // 동시에 해당 트래커 id를 emitPredictionEvent로 재발행해, 차트/AI패널의 getPrediction
    // 30초 폴링이 다음 주기를 기다리지 않고 즉시 갱신되게 한다(무효화 시 점선 지연 제거).
    source.addEventListener('prediction', (e: MessageEvent<string>) => {
      const payload = JSON.parse(e.data) as PredictionStreamEvent
      emitPredictionEvent(payload.trackerId)
      setLastPredictionAt(new Date())
      reload()
    })

    // anomaly 이벤트(활성화/해제 전이)도 CAUTION 상태를 바꾸므로 같은 방식으로 전체 재조회한다.
    // reading patch는 BREACH 축만 다루기 때문에(위 주석), CAUTION의 시의성은 이 구독이 담당한다.
    source.addEventListener('anomaly', () => reload())

    source.onerror = () => {
      setError(new Error('실시간 연결(SSE)에 문제가 발생했습니다.'))
    }

    return () => {
      cancelled = true
      source.close()
    }
  }, [statusFilter])

  return { trackers, error, loading, updatedAt, newAlertCount, resetNewAlertCount, lastPredictionAt }
}
