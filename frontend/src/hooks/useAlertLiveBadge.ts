import { useEffect, useState } from 'react'
import { BASE_URL } from '../api/client'

interface AlertLiveBadgeResult {
  newAlertCount: number
  reset: () => void
}

/**
 * 알림 탭 상단 Live 배지 전용 — 목록 자체는 폴링(GET /alerts)으로 갱신하고, 이 훅은
 * SSE `alert` 이벤트가 올 때마다 "탭을 안 보고 있는 동안 새로 발생한 건수"만 센다.
 */
export function useAlertLiveBadge(): AlertLiveBadgeResult {
  const [count, setCount] = useState(0)

  useEffect(() => {
    const source = new EventSource(`${BASE_URL}/api/v1/stream`)
    source.addEventListener('alert', () => setCount((c) => c + 1))
    return () => source.close()
  }, [])

  return { newAlertCount: count, reset: () => setCount(0) }
}
