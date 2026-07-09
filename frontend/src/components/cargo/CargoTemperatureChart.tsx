import { useMemo } from 'react'
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getPrediction } from '../../api/prediction'
import { getReadings } from '../../api/readings'
import { usePolling } from '../../hooks/usePolling'
import { withForecast } from '../../lib/forecastChart'
import { DashboardCard } from '../dashboard/DashboardCard'

interface CargoTemperatureChartProps {
  trackerId: string
  thresholdTemp: number | null
}

const RANGE_HOURS = 6

const formatTs = (iso: string) => new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })

// 화물 관리 상세 패널 전용 — 트래커가 이미 고정돼 있어 대시보드의 드롭다운/기간버튼은 불필요.
export function CargoTemperatureChart({ trackerId, thresholdTemp }: CargoTemperatureChartProps) {
  const { data } = usePolling(
    () => {
      const to = new Date()
      const from = new Date(to.getTime() - RANGE_HOURS * 60 * 60 * 1000)
      return getReadings(trackerId, from.toISOString(), to.toISOString())
    },
    30_000,
    [trackerId],
  )

  const { data: prediction } = usePolling(() => getPrediction(trackerId), 30_000, [trackerId])

  const chartData = useMemo(() => {
    const actual = (data?.readings ?? []).map((r) => ({ ts: formatTs(r.ts), temperature: r.temperature }))
    return withForecast(actual, prediction, formatTs)
  }, [data, prediction])

  return (
    <DashboardCard title="온도 추이 (실측)" meta="최근 6시간" bodyClassName="px-7 pb-7">
      {chartData.length === 0 ? (
        <p className="text-sm text-neutral-500">최근 6시간 내 데이터가 없습니다.</p>
      ) : (
        <ResponsiveContainer width="100%" height={180}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1d1d1d" />
            <XAxis dataKey="ts" stroke="#737373" fontSize={11} />
            <YAxis stroke="#737373" fontSize={11} unit="℃" width={40} />
            <Tooltip
              contentStyle={{ background: '#18181b', border: '1px solid #262626', fontSize: 12 }}
              labelStyle={{ color: '#a3a3a3' }}
            />
            {thresholdTemp != null && (
              <ReferenceLine
                y={thresholdTemp}
                stroke="#ff6b6b"
                strokeDasharray="4 4"
                label={{ value: `임계 ${thresholdTemp}℃`, fill: '#ff6b6b', fontSize: 10, position: 'insideTopRight' }}
              />
            )}
            <Line type="monotone" dataKey="temperature" name="실측" stroke="#5ea9ff" strokeWidth={2} dot={false} />
            <Line
              type="monotone"
              dataKey="forecastTemperature"
              name="예측"
              stroke="#f5a623"
              strokeWidth={2}
              strokeDasharray="5 5"
              dot={false}
              connectNulls
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </DashboardCard>
  )
}
