import { Bar, BarChart, CartesianGrid, Cell, Label, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PredictionEpisodeSummary } from '../../types/adminMetrics'
import { DashboardCard } from '../dashboard/DashboardCard'

interface ReportRescueChartProps {
  episodes: PredictionEpisodeSummary[]
}

// 시나리오 테이블과 동일한 episodes[]를 세 버킷으로 묶어 시각화한다 — 새 집계 아님.
// 이탈·구조(오탐)·진행중/무효화를 하나의 stacked bar에 쌓지 않고 막대 3개로 나란히 비교한다
// (리뷰 지적: 쌓은 막대는 작은 값이 묻혀 안 보였다).
export function ReportRescueChart({ episodes }: ReportRescueChartProps) {
  const breached = episodes.filter((e) => e.status === 'BREACHED').length
  const rescued = episodes.filter((e) => e.status === 'CANCELED' || e.status === 'EXPIRED').length
  const pending = episodes.filter((e) => e.status === 'ACTIVE' || e.status === 'INVALIDATED').length

  const data = [
    { name: '이탈', count: breached, fill: '#ff6b6b' },
    { name: '구조(오탐)', count: rescued, fill: '#4ade80' },
    { name: '진행중/무효화', count: pending, fill: '#525252' },
  ]

  return (
    <DashboardCard title="구조 성공 분석" meta={`총 ${episodes.length}건 · 시나리오 결과와 동일 데이터`}>
      {episodes.length === 0 ? (
        <p className="text-sm text-neutral-500">해당 기간에 예측 에피소드가 없습니다.</p>
      ) : (
        <ResponsiveContainer width="100%" height={260}>
          <BarChart data={data} margin={{ top: 16, bottom: 8 }} barSize={64}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1d1d1d" />
            <XAxis dataKey="name" stroke="#737373" fontSize={12} />
            <YAxis stroke="#737373" fontSize={12} allowDecimals={false}>
              <Label value="에피소드 수" angle={-90} position="insideLeft" fill="#737373" fontSize={11} />
            </YAxis>
            <Tooltip contentStyle={{ background: '#18181b', border: '1px solid #262626', fontSize: 12 }} />
            <Bar dataKey="count">
              {data.map((d) => (
                <Cell key={d.name} fill={d.fill} />
              ))}
              <LabelList dataKey="count" position="top" fill="#e5e5e5" fontSize={13} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </DashboardCard>
  )
}
