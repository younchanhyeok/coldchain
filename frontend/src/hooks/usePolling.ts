import { useEffect, useRef, useState } from 'react'

interface PollingResult<T> {
  data: T | null
  error: Error | null
  loading: boolean
}

/**
 * fetchFn을 즉시 1회 실행하고 이후 intervalMs마다 반복한다.
 * deps가 바뀌면 기존 폴링을 정리하고 새로 시작한다(예: 선택된 트래커가 바뀔 때).
 */
export function usePolling<T>(
  fetchFn: () => Promise<T>,
  intervalMs: number,
  deps: unknown[] = [],
): PollingResult<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState(true)
  const fetchFnRef = useRef(fetchFn)
  fetchFnRef.current = fetchFn

  useEffect(() => {
    let cancelled = false

    async function tick() {
      try {
        const result = await fetchFnRef.current()
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error(String(err)))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    setLoading(true)
    tick()
    const id = setInterval(tick, intervalMs)

    return () => {
      cancelled = true
      clearInterval(id)
    }
    // deps는 호출부가 폴링 재시작 조건을 직접 지정하는 용도라 fetchFn은 의도적으로 제외한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, error, loading }
}
