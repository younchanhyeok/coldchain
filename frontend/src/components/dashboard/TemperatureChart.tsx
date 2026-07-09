import { useMemo, useState } from 'react'
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { getPrediction } from '../../api/prediction'
import { getReadings } from '../../api/readings'
import { usePolling } from '../../hooks/usePolling'
import { usePredictionRefreshSignal } from '../../hooks/usePredictionRefreshSignal'
import { withForecast } from '../../lib/forecastChart'
import type { TrackerSummary } from '../../types/tracker'

const formatTs = (iso: string) => new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })

// 시뮬레이터가 보통 수십 분 단위로 도는 데모 데이터 특성상, 시간 단위 프리셋(6~48시간)은
// 전부 같은 결과를 반환해 버튼이 눈에 띄게 다르지 않았다 — 분 단위로 좁혀 실제 체감 차이를 낸다.
const RANGE_OPTIONS = [
  { label: '1분', minutes: 1 },
  { label: '5분', minutes: 5 },
  { label: '10분', minutes: 10 },
  { label: '1시간', minutes: 60 },
]

interface TemperatureChartProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

export function TemperatureChart({ trackers, selectedTrackerId, onSelectTracker }: TemperatureChartProps) {
  const [rangeMinutes, setRangeMinutes] = useState(60)
  const selectedTracker = trackers.find((t) => t.trackerId === selectedTrackerId) ?? null

  // 폴링 대상은 선택된 트래커 하나의 시계열뿐이다. trackerId/rangeMinutes가 바뀌면
  // usePolling의 deps로 인해 기존 타이머가 정리되고 새로 시작된다.
  const { data } = usePolling(
    () => {
      if (!selectedTrackerId) return Promise.resolve(null)
      const to = new Date()
      const from = new Date(to.getTime() - rangeMinutes * 60 * 1000)
      return getReadings(selectedTrackerId, from.toISOString(), to.toISOString())
    },
    30_000,
    [selectedTrackerId, rangeMinutes],
  )

  const predictionSignal = usePredictionRefreshSignal(selectedTrackerId)
  const { data: prediction } = usePolling(
    () => (selectedTrackerId ? getPrediction(selectedTrackerId) : Promise.resolve(null)),
    30_000,
    [selectedTrackerId, predictionSignal],
  )

  const chartData = useMemo(() => {
    const actual = (data?.readings ?? []).map((r) => ({ ts: formatTs(r.ts), temperature: r.temperature }))
    return withForecast(actual, prediction, formatTs)
  }, [data, prediction])

  return (
    <div
      className="rounded-card border border-border bg-card p-7 shadow-card transition-all duration-[250ms]
        ease-out hover:border-[#333333] hover:shadow-card-hover"
    >
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-neutral-200">온도 추이 (실측)</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="h-11 rounded-control border border-border bg-card px-3 text-sm text-neutral-200"
            value={selectedTrackerId ?? ''}
            onChange={(e) => onSelectTracker(e.target.value)}
          >
            {trackers.map((t) => (
              <option key={t.trackerId} value={t.trackerId}>
                {t.trackerId}
              </option>
            ))}
          </select>
          <div className="flex gap-1">
            {RANGE_OPTIONS.map((opt) => (
              <button
                key={opt.minutes}
                type="button"
                className={`rounded-md px-2 py-1 text-xs ${
                  rangeMinutes === opt.minutes ? 'bg-primary/20 text-primary' : 'text-neutral-500 hover:bg-card-hover'
                }`}
                onClick={() => setRangeMinutes(opt.minutes)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {!selectedTracker ? (
        <p className="text-sm text-neutral-500">트래커를 선택하세요.</p>
      ) : chartData.length === 0 ? (
        <p className="text-sm text-neutral-500">선택한 구간에 데이터가 없습니다.</p>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1d1d1d" />
            <XAxis dataKey="ts" stroke="#737373" fontSize={12} />
            <YAxis stroke="#737373" fontSize={12} unit="℃" />
            <Tooltip
              contentStyle={{ background: '#18181b', border: '1px solid #262626', fontSize: 12 }}
              labelStyle={{ color: '#a3a3a3' }}
            />
            <ReferenceLine
              y={selectedTracker.thresholdTemp}
              stroke="#ff6b6b"
              strokeDasharray="4 4"
              label={{
                value: `임계 ${selectedTracker.thresholdTemp}℃`,
                fill: '#ff6b6b',
                fontSize: 11,
                position: 'insideTopRight',
              }}
            />
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
    </div>
  )
}
