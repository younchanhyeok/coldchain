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
import { getReadings } from '../../api/readings'
import { usePolling } from '../../hooks/usePolling'
import type { TrackerSummary } from '../../types/tracker'

const RANGE_OPTIONS = [
  { label: '6시간', hours: 6 },
  { label: '12시간', hours: 12 },
  { label: '24시간', hours: 24 },
  { label: '48시간', hours: 48 },
]

interface TemperatureChartProps {
  trackers: TrackerSummary[]
  selectedTrackerId: string | null
  onSelectTracker: (trackerId: string) => void
}

export function TemperatureChart({ trackers, selectedTrackerId, onSelectTracker }: TemperatureChartProps) {
  const [rangeHours, setRangeHours] = useState(24)
  const selectedTracker = trackers.find((t) => t.trackerId === selectedTrackerId) ?? null

  // 폴링 대상은 선택된 트래커 하나의 시계열뿐이다. trackerId/rangeHours가 바뀌면
  // usePolling의 deps로 인해 기존 타이머가 정리되고 새로 시작된다.
  const { data } = usePolling(
    () => {
      if (!selectedTrackerId) return Promise.resolve(null)
      const to = new Date()
      const from = new Date(to.getTime() - rangeHours * 60 * 60 * 1000)
      return getReadings(selectedTrackerId, from.toISOString(), to.toISOString())
    },
    30_000,
    [selectedTrackerId, rangeHours],
  )

  const chartData = useMemo(
    () =>
      (data?.readings ?? []).map((r) => ({
        ts: new Date(r.ts).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
        temperature: r.temperature,
      })),
    [data],
  )

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-200">온도 추이 (실측)</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-200"
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
                key={opt.hours}
                type="button"
                className={`rounded-md px-2 py-1 text-xs ${
                  rangeHours === opt.hours ? 'bg-blue-600/30 text-blue-300' : 'text-slate-500 hover:bg-slate-800'
                }`}
                onClick={() => setRangeHours(opt.hours)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {!selectedTracker ? (
        <p className="text-sm text-slate-500">트래커를 선택하세요.</p>
      ) : chartData.length === 0 ? (
        <p className="text-sm text-slate-500">선택한 구간에 데이터가 없습니다.</p>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="ts" stroke="#64748b" fontSize={12} />
            <YAxis stroke="#64748b" fontSize={12} unit="℃" />
            <Tooltip
              contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', fontSize: 12 }}
              labelStyle={{ color: '#94a3b8' }}
            />
            <ReferenceLine
              y={selectedTracker.thresholdTemp}
              stroke="#f87171"
              strokeDasharray="4 4"
              label={{
                value: `임계 ${selectedTracker.thresholdTemp}℃`,
                fill: '#f87171',
                fontSize: 11,
                position: 'insideTopRight',
              }}
            />
            <Line type="monotone" dataKey="temperature" name="실측" stroke="#38bdf8" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
