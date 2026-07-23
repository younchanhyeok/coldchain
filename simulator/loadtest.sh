#!/usr/bin/env bash
# 부하테스트 원커맨드 — 워밍업+측정을 한 번에 돌리고 산출물을 reports/에 남긴다.
#
# 사용법: ./loadtest.sh <trackers> [measure_s=300] [warmup_s=60]
# 환경변수: TARGET(기본 http://localhost:8080), INTERVAL(5), SEED(42)
#   INSTANCES(1) — 2로 주면 2번째 백엔드를 SERVER_PORT=8081로 띄워 같은 Kafka 그룹에 합류시킨다
#                  (M8 수평 확장 실증: 파티션 3+3 재분배로 소비 용량 2배). 끝나면 자동 정리(trap).
#   EXTRA_PORT(8081) — 2번째 인스턴스 포트.
#
# 전제: infra docker compose 기동 + 백엔드 bootRun 완료(1번째 인스턴스=TARGET).
# 2번째 인스턴스는 이 스크립트가 띄운다 — 1번째가 이미 마이그레이션을 끝낸 뒤라 Flyway 락 충돌 없음.
# JWT_SECRET/ADMIN_KEY는 이 스크립트 환경에서 2번째 인스턴스로 그대로 상속된다(M5~ fail-fast).
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
INSTANCES=${INSTANCES:-1}
EXTRA_PORT=${EXTRA_PORT:-8081}
# venv가 있으면 우선 사용 — macOS 시스템 python3는 PEP 668로 pip install이 막혀 aiohttp가 없다.
PYTHON=${PYTHON:-$([ -x .venv/bin/python ] && echo .venv/bin/python || echo python3)}
COMPOSE=(docker compose -f ../infra/docker-compose.yml)

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="reports/${STAMP}-t${TRACKERS}-i${INSTANCES}"
mkdir -p "$OUT"
READY_FILE="$OUT/.load-started"

# 백그라운드 자식(2번째 인스턴스·lag 샘플러) 정리 — 중단(Ctrl+C)에도 orphan 백엔드가 안 남게.
EXTRA_PID=""
LAG_PID=""
cleanup() {
  [ -n "$LAG_PID" ] && kill "$LAG_PID" 2>/dev/null || true
  if [ -n "$EXTRA_PID" ]; then
    echo "2번째 인스턴스(pid $EXTRA_PID) 정리..."
    kill "$EXTRA_PID" 2>/dev/null || true
    wait "$EXTRA_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# macOS 기본 FD 상한(256)으로는 수천 개 동시 커넥션을 못 연다.
ulimit -n 65536 2>/dev/null || echo "(ulimit -n 조정 실패 — FD 상한 수동 확인 필요)"

curl -sf "${TARGET}/actuator/health" > /dev/null \
  || { echo "백엔드 미기동: ${TARGET} — bootRun 후 다시 실행"; exit 1; }

# 2번째 인스턴스 — 1번째가 이미 떠 있는(마이그레이션 완료) 상태에서 순차 기동하므로 Flyway 락 없음.
# 같은 DB/Redis/Kafka(env 기본 localhost)를 공유하고 그룹 coldchain-ingest에 합류 → 파티션 재분배.
if [ "$INSTANCES" -ge 2 ]; then
  echo "2번째 백엔드 기동 (SERVER_PORT=${EXTRA_PORT}) — Kafka 그룹 합류·파티션 재분배..."
  ( cd ../backend && SERVER_PORT="$EXTRA_PORT" ./gradlew bootRun ) > "$OUT/backend-${EXTRA_PORT}.log" 2>&1 &
  EXTRA_PID=$!
  for _ in $(seq 1 90); do
    if curl -sf "http://localhost:${EXTRA_PORT}/actuator/health" > /dev/null 2>&1; then break; fi
    sleep 3
  done
  curl -sf "http://localhost:${EXTRA_PORT}/actuator/health" > /dev/null 2>&1 \
    || { echo "2번째 인스턴스 기동 실패 — $OUT/backend-${EXTRA_PORT}.log 확인"; exit 1; }
  echo "2번째 인스턴스 UP — 컨슈머 그룹 리밸런스 안정화 대기 15s"; sleep 15
fi

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

# 부하 중 컨슈머 랙 시계열 — 종료 시 1회 스냅샷만으론 "해소 속도"(드레인 커브)를 못 본다.
# 10초마다 그룹 전체 랙 합을 찍어 1대 vs 2대의 누적/드레인 차이를 곡선으로 남긴다(kafka 모드만).
if [ "${INGEST_MODE:-kafka}" != "direct" ]; then
  ( echo "# ts total_lag  (INSTANCES=$INSTANCES, TRACKERS=$TRACKERS)" > "$OUT/lag-timeseries.txt"
    while true; do
      lag=$("${COMPOSE[@]}" exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
              --bootstrap-server localhost:9092 --describe --group coldchain-ingest 2>/dev/null \
            | awk '$6 ~ /^[0-9]+$/ {sum+=$6} END {print sum+0}')
      echo "$(date +%H:%M:%S) ${lag:-NA}" >> "$OUT/lag-timeseries.txt"
      sleep 10
    done ) &
  LAG_PID=$!
fi

"$PYTHON" run.py --trackers "$TRACKERS" --interval "$INTERVAL" --profile normal \
  --target "$TARGET" --seed "$SEED" --duration "$MEASURE" --warmup "$WARMUP" \
  --ready-file "$READY_FILE" --report-out "$OUT/ingest.json" 2>&1 | tee "$OUT/simulator.log"

[ -n "$LAG_PID" ] && { kill "$LAG_PID" 2>/dev/null || true; LAG_PID=""; }
curl -s "${TARGET}/actuator/prometheus" > "$OUT/prometheus-after.txt" || true
# kafka 모드면 컨슈머 랙 캡처 — "202는 빨라졌는데 다운스트림이 따라오나"의 직접 증거.
"${COMPOSE[@]}" exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group coldchain-ingest > "$OUT/consumer-lag.txt" 2>&1 || true
"${COMPOSE[@]}" exec -T postgres psql -U "${DB_USER:-coldchain}" -d "${DB_NAME:-coldchain}" -c \
  "SELECT calls, round(mean_exec_time::numeric, 2) AS mean_ms,
          round(total_exec_time::numeric) AS total_ms, left(query, 120) AS query
     FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 15;" \
  > "$OUT/pg-top-queries.txt" 2>&1 || true

wait "$SSE_PID" "$API_PID" || true

echo ""
echo "── SSE e2e ──"
tail -n 5 "$OUT/sse_probe.log"
if [ "$INSTANCES" -ge 2 ]; then
  echo "  ⚠ SSE 주의: SseBroadcaster는 인프로세스라 프로브(TARGET=8080)는 8080이 소비한 파티션의"
  echo "    리딩만 관측한다(8081 소비분은 못 봄). 1대·2대 동일 조건이라 비교는 공정하나, SSE 절대"
  echo "    도달률은 2대에서 저평가된다 — 팬아웃 정석은 Redis pub/sub(보류, YAGNI). 랙으로 소비를 판단."
fi
echo "── 컨슈머 랙(드레인) ──"
tail -n 4 "$OUT/lag-timeseries.txt" 2>/dev/null || echo "(direct 모드 — 랙 없음)"
echo "  파티션 분배 스냅샷: $OUT/consumer-lag.txt (2대면 CONSUMER-ID 2개로 3+3 분할)"
echo "── 대시보드 API ──"
tail -n 6 "$OUT/api_probe.log"
echo ""
echo "완료 — 산출물: simulator/$OUT"
