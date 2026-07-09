import { useEffect, useState } from 'react'

interface RiskLiveBadgeProps {
  lastPredictionAt: Date | null
}

// "최근 분석 N초 전"은 SSE prediction 이벤트 수신 시각 그대로 — 폴링 흉내가 아닌 정직한
// 실시간 표시(화면_탭_구성.md). 아직 이벤트가 없으면 "분석 대기 중"으로 있는 그대로 보여준다.
export function RiskLiveBadge({ lastPredictionAt }: RiskLiveBadgeProps) {
  const [, tick] = useState(0)

  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 1_000)
    return () => clearInterval(id)
  }, [])

  const label = (() => {
    if (!lastPredictionAt) return '분석 대기 중'
    const seconds = Math.floor((Date.now() - lastPredictionAt.getTime()) / 1000)
    if (seconds < 60) return `최근 분석 ${seconds}초 전`
    return `최근 분석 ${Math.floor(seconds / 60)}분 전`
  })()

  return (
    <div className="flex items-center gap-2 text-sm text-neutral-400">
      <span className="flex items-center gap-1.5 font-medium text-neutral-200">
        <span className="h-2 w-2 animate-pulse rounded-full bg-success" />
        AI Monitoring ● LIVE
      </span>
      <span className="text-neutral-600">·</span>
      <span>{label}</span>
    </div>
  )
}
