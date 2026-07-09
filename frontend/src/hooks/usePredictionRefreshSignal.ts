import { useEffect, useState } from 'react'

type Listener = (trackerId: string) => void

const listeners = new Set<Listener>()

// useTrackerStream이 이미 앱 전체가 공유하는 단일 EventSource를 갖고 있으므로(SSE 연결 늘리지
// 않기 위함), 그 훅이 받은 prediction 이벤트를 여기로 재발행해 다른 컴포넌트가 별도 연결 없이
// 구독할 수 있게 한다.
export function emitPredictionEvent(trackerId: string) {
  listeners.forEach((listener) => listener(trackerId))
}

/**
 * 해당 트래커의 prediction SSE 이벤트가 올 때마다 값이 바뀐다 — usePolling의 deps에 넣으면
 * 30초 폴링을 기다리지 않고 즉시 재조회한다(예: 급변→INVALIDATED 시 예측 점선이 지연 없이 사라짐).
 */
export function usePredictionRefreshSignal(trackerId: string | null): number {
  const [seq, setSeq] = useState(0)

  useEffect(() => {
    if (!trackerId) return
    const listener: Listener = (id) => {
      if (id === trackerId) setSeq((s) => s + 1)
    }
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  }, [trackerId])

  return seq
}
