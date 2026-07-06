# coldchain

콜드체인 IoT 관제 시스템 — 백엔드 포트폴리오 프로젝트.

## 개요

의약품·식품 등 콜드체인 배송은 온도가 임계값을 벗어난 **후에** 알아채면 이미 늦다. 이 프로젝트는 트래커(온도·위치) 데이터를 실시간 수집해 이상 징후를 탐지하는 것을 넘어, 현재 추세로부터 **N분 후 온도 이탈을 예측**해 문제가 벌어지기 전에 경고한다. 급변 이벤트(냉동기 고장 등)가 감지되면 예측은 즉시 무효화하고 알림으로 전환하는 안전한 실패 설계를 따른다.

## 핵심 기능

- **예측(L3, 핵심)** — 최근 온도 추이를 회귀 모델에 넣어 임계 이탈 시각·리드타임을 산출, 사전 경고. 급변 이벤트 시 예측 `INVALIDATED` + 즉시 알림 전환.
- **이상탐지(L2)** — Redis 윈도우 기반 z-score/이동평균으로 급변 vs 점진적 변화 구분.
- **공간 쿼리(PostGIS)** — 배송 경로·트래커 위치 기반 지도 표시, 반경/경로 쿼리.
- **역할 기반 멀티뷰** — 화주(JWT)/수령기관(매직링크)별로 같은 배송을 다른 인가 스코프로 조회.
- **고쓰루풋 수집(L1)** — 다수 트래커의 동시 전송을 저장 + 최신상태(`tracker_latest`) upsert로 처리, out-of-order 수신은 낙관적 락으로 방어.

## 아키텍처

```
[트래커 N] ─▶ [L1 수집] ─▶ readings 저장 + tracker_latest UPSERT(낙관적 락)
                  │
                  ▼
             [L2 이상탐지] Redis 윈도우 · z-score/이동평균 · 급변 vs 점진 구분
                  │
                  ▼
             [L3 예측] Python FastAPI 호출 (타임아웃 2s + fallback) ─▶ 이탈시각·리드타임 산출
                  │
                  ▼
        알림 파이프라인(Slack) + SSE ─▶ 역할별 대시보드(React, 지도 포함)
```

핵심 불변식: **예측·알림이 죽어도 수집·저장은 무중단.**

## 스택

| 영역 | 선택 |
|---|---|
| Backend | Spring Boot 3.x (Java 17, Gradle) |
| 수집 큐 | Kafka (M6 도입, 그전엔 HTTP) |
| DB | PostgreSQL + PostGIS (+TimescaleDB, M6 전환) |
| 캐시/윈도우 | Redis |
| 예측(L3) | Python 3.12 + FastAPI + scikit-learn |
| 실시간 | SSE |
| 인증 | JWT(화주) + 매직링크(수령기관) + 디바이스 키 + 어드민 키 |
| Frontend | React + 카카오/네이버 지도 |
| 배포 | 로컬 docker-compose |

## 마일스톤

| # | 목표 | 완료 기준 | 상태 |
|---|---|---|---|
| M0 | 기반 공사 — 리포·DB 스키마 v1·docker-compose·CI | `docker compose up` 한 방에 빈 시스템 기동 | 진행 중 |
| M1 | 최소 파이프라인 — 시뮬레이터→수집→저장→조회 | 시뮬레이터 50개 데이터가 DB에 쌓이고 API로 조회 | - |
| M2 | 실시간 대시보드 — SSE·지도·온도 차트 | 지도 마커가 움직이고 차트가 실시간으로 흐름 | - |
| M3 | 이상탐지(L2) — 임계+통계 탐지·Slack 알림 | 급상승 시나리오에 Slack 경고 도착 | - |
| M4 | 예측(L3) ★핵심 — 선형회귀 예측·선제 경고·평가지표 | 예측 성능이 리드타임·오탐률 수치로 측정됨 | - |
| M5 | 역할 멀티뷰 — JWT·매직링크·인가 스코핑 테스트 | 같은 배송을 역할별 다른 화면·범위로 조회 | - |
| M6 | 스케일 — 부하테스트→Kafka·Timescale 전환 | 개선 전/후 수치 비교 리포트 | - |
| M7 | 예측 심화 — 다변량·평가 자동화 | v1 vs v2 모델 수치 비교 | - |

## 실행

```bash
docker compose -f infra/docker-compose.yml up -d   # PG(+PostGIS)·Redis·(M6~)Kafka
cd backend && ./gradlew bootRun
cd prediction && uvicorn app.main:app --port 8000
cd frontend && npm run dev
```

시뮬레이터:

```bash
cd simulator
python run.py --trackers 50 --interval 5 --profile normal --target http://localhost:8080
```

API 계약은 [`docs/API_명세.md`](docs/API_명세.md) 참고.

## 컨벤션 · 깃 전략

상세는 [`CLAUDE.md`](CLAUDE.md) 참고. 요약:

- 브랜치: `main` 단일 + 기능 단위 `feat/...` → merge
- 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:` …)
- 마일스톤 완료 시 태그 `m0`~`m7`
