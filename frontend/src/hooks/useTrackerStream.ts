import { useEffect, useRef, useState } from 'react'
import { BASE_URL } from '../api/client'
import { getTrackers } from '../api/trackers'
import type { StatusFilter } from '../components/layout/TopBar'
import type { ReadingStreamEvent } from '../types/stream'
import type { TrackerSummary } from '../types/tracker'

interface TrackerStreamResult {
  trackers: TrackerSummary[]
  error: Error | null
  loading: boolean
  updatedAt: Date | null
}

/**
 * 초기 목록은 REST로 불러오고, 이후 갱신은 SSE(`/api/v1/stream`)의 reading 이벤트로 받는다.
 * 연결이 열릴 때마다(최초 연결 포함, 브라우저의 자동 재연결 포함) REST 전체 재조회로 리싱크한다
 * (API 명세 §5.1의 MVP 재연결 정책).
 */
export function useTrackerStream(statusFilter: StatusFilter): TrackerStreamResult {
  const [trackers, setTrackers] = useState<TrackerSummary[]>([])
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState(true)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const statusFilterRef = useRef(statusFilter)
  statusFilterRef.current = statusFilter

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
        const next = current.map((t) => {
          if (t.trackerId !== payload.trackerId) return t
          return {
            ...t,
            lastTemperature: payload.temperature,
            lastPosition: { lat: payload.lat, lon: payload.lon },
            lastReportedAt: payload.ts,
            status: payload.status,
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

    source.onerror = () => {
      setError(new Error('실시간 연결(SSE)에 문제가 발생했습니다.'))
    }

    return () => {
      cancelled = true
      source.close()
    }
  }, [statusFilter])

  return { trackers, error, loading, updatedAt }
}
