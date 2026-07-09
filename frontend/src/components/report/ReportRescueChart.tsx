import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PredictionEpisodeSummary } from '../../types/adminMetrics'
import { DashboardCard } from '../dashboard/DashboardCard'

interface ReportRescueChartProps {
  episodes: PredictionEpisodeSummary[]
}

// 시나리오 테이블과 동일한 episodes[]를 세 버킷으로 묶어 시각화한다 — 새 집계 아님.
export function ReportRescueChart({ episodes }: ReportRescueChartProps) {
  const breached = episodes.filter((e) => e.status === 'BREACHED').length
  const rescued = episodes.filter((e) => e.status === 'CANCELED' || e.status === 'EXPIRED').length
  const pending = episodes.filter((e) => e.status === 'ACTIVE' || e.status === 'INVALIDATED').length

  const data = [{ name: '이번 기간', 이탈: breached, '구조(오탐)': rescued, '진행중/무효화': pending }]

  return (
    <DashboardCard title="구조 성공 분석" meta="시나리오 결과와 동일 데이터">
      {episodes.length === 0 ? (
        <p className="text-sm text-neutral-500">해당 기간에 예측 에피소드가 없습니다.</p>
      ) : (
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={data} layout="vertical" margin={{ left: 24 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1d1d1d" />
            <XAxis type="number" stroke="#737373" fontSize={12} allowDecimals={false} />
            <YAxis type="category" dataKey="name" stroke="#737373" fontSize={12} width={80} />
            <Tooltip contentStyle={{ background: '#18181b', border: '1px solid #262626', fontSize: 12 }} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Bar dataKey="이탈" stackId="a" fill="#ff6b6b" />
            <Bar dataKey="구조(오탐)" stackId="a" fill="#4ade80" />
            <Bar dataKey="진행중/무효화" stackId="a" fill="#525252" />
          </BarChart>
        </ResponsiveContainer>
      )}
    </DashboardCard>
  )
}
