import { Sparkles } from 'lucide-react'
import { DashboardCard } from '../dashboard/DashboardCard'

// 데이터 없는 지표는 placeholder로만 표시 — 가짜 값을 절대 넣지 않는다(M4 가드).
export function CargoPredictionPlaceholder() {
  return (
    <DashboardCard title="AI 예측">
      <div className="flex items-center gap-3 text-neutral-600">
        <Sparkles size={18} />
        <span className="text-sm">M4 예측 이후 제공</span>
      </div>
    </DashboardCard>
  )
}
