#!/usr/bin/env bash
# 부하테스트 원커맨드 — 워밍업+측정을 한 번에 돌리고 산출물을 reports/에 남긴다.
#
# 사용법: ./loadtest.sh <trackers> [measure_s=300] [warmup_s=60]
# 환경변수: TARGET(기본 http://localhost:8080), INTERVAL(5), SEED(42)
#
# 전제: infra docker compose 기동 + 백엔드 bootRun 완료.
# fresh 볼륨 여부는 호출자가 결정한다(측정 프로토콜: 실측 비교 런은 반드시
# `docker compose -f infra/docker-compose.yml down -v` 후 재기동한 상태에서 시작).
#
# 산출물(reports/<stamp>-t<N>/):
#   ingest.json / sse.json / api.json     각 측정기 최종 리포트
#   prometheus-before.txt / -after.txt    actuator 스냅샷(서버측 원인 규명용)
#   pg-top-queries.txt                    pg_stat_statements 상위 쿼리
#   simulator.log / sse_probe.log / api_probe.log
set -euo pipefail
cd "$(dirname "$0")"

TRACKERS=${1:?사용법: ./loadtest.sh <trackers> [measure_s=300] [warmup_s=60]}
MEASURE=${2:-300}
WARMUP=${3:-60}
TARGET=${TARGET:-http://localhost:8080}
INTERVAL=${INTERVAL:-5}
SEED=${SEED:-42}
# venv가 있으면 우선 사용 — macOS 시스템 python3는 PEP 668로 pip install이 막혀 aiohttp가 없다.
PYTHON=${PYTHON:-$([ -x .venv/bin/python ] && echo .venv/bin/python || echo python3)}
COMPOSE=(docker compose -f ../infra/docker-compose.yml)

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="reports/${STAMP}-t${TRACKERS}"
mkdir -p "$OUT"
READY_FILE="$OUT/.load-started"

# macOS 기본 FD 상한(256)으로는 수천 개 동시 커넥션을 못 연다.
ulimit -n 65536 2>/dev/null || echo "(ulimit -n 조정 실패 — FD 상한 수동 확인 필요)"

curl -sf "${TARGET}/actuator/health" > /dev/null \
  || { echo "백엔드 미기동: ${TARGET} — bootRun 후 다시 실행"; exit 1; }

# pg_stat_statements — preload는 compose command에 있고, extension은 fresh 볼륨마다 다시 만든다.
"${COMPOSE[@]}" exec -T postgres psql -U "${DB_USER:-coldchain}" -d "${DB_NAME:-coldchain}" \
  -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements; SELECT pg_stat_statements_reset();" \
  > /dev/null 2>&1 || echo "(pg_stat_statements 리셋 실패 — preload 설정 확인)"

curl -s "${TARGET}/actuator/prometheus" > "$OUT/prometheus-before.txt" || true

TOTAL=$((WARMUP + MEASURE))

# 프로브들은 등록 완료(READY_FILE 생성) 시점부터 측정 시계를 시작한다 —
# 등록 소요가 트래커 수에 비례해 달라져 프로세스 시작 시각은 기준이 못 된다.
"$PYTHON" sse_probe.py --target "$TARGET" --wait-for "$READY_FILE" \
  --duration $((TOTAL + 10)) --warmup "$WARMUP" \
  --report-out "$OUT/sse.json" > "$OUT/sse_probe.log" 2>&1 &
SSE_PID=$!
"$PYTHON" api_probe.py --target "$TARGET" --wait-for "$READY_FILE" \
  --duration $((TOTAL + 10)) --warmup "$WARMUP" \
  --report-out "$OUT/api.json" > "$OUT/api_probe.log" 2>&1 &
API_PID=$!

"$PYTHON" run.py --trackers "$TRACKERS" --interval "$INTERVAL" --profile normal \
  --target "$TARGET" --seed "$SEED" --duration "$MEASURE" --warmup "$WARMUP" \
  --ready-file "$READY_FILE" --report-out "$OUT/ingest.json" 2>&1 | tee "$OUT/simulator.log"

curl -s "${TARGET}/actuator/prometheus" > "$OUT/prometheus-after.txt" || true
"${COMPOSE[@]}" exec -T postgres psql -U "${DB_USER:-coldchain}" -d "${DB_NAME:-coldchain}" -c \
  "SELECT calls, round(mean_exec_time::numeric, 2) AS mean_ms,
          round(total_exec_time::numeric) AS total_ms, left(query, 120) AS query
     FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 15;" \
  > "$OUT/pg-top-queries.txt" 2>&1 || true

wait "$SSE_PID" "$API_PID" || true

echo ""
echo "── SSE e2e ──"
tail -n 5 "$OUT/sse_probe.log"
echo "── 대시보드 API ──"
tail -n 6 "$OUT/api_probe.log"
echo ""
echo "완료 — 산출물: simulator/$OUT"
