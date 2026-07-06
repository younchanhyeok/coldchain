# CLAUDE.md — coldchain

콜드체인 IoT 관제 시스템. 개인 백엔드 포트폴리오 프로젝트.
IoT 온도·위치 트래커 데이터를 실시간 수집하고, 온도 이탈을 **사전에 예측·경고**하는 3레이어 파이프라인.

---

## 핵심 원칙 (모든 작업에 적용)

- **예측(L3)이 프로젝트 간판.** 수집·알림·SSE·인프라는 배관 — 최소로 구현하고, 시간·설계 예산은 예측·스케일(고쓰루풋)·공간(PostGIS)·시계열에 쓴다.
- **기술 도입은 "필요를 겪은 뒤".** M1~M5는 단순 HTTP + plain PostgreSQL(+PostGIS). Kafka·TimescaleDB는 M6에서 부하테스트로 병목을 확인한 뒤 전환 (before/after 수치 기록).
- **안전한 실패 설계.** 급변 이벤트(냉동기 고장·문 개폐) 감지 시 예측을 `INVALIDATED` 처리하고 즉시 알림으로 전환. 예측 한계를 숨기지 않는다.
- **핵심 불변식 (NFR-3):** 예측·알림이 죽어도 수집·저장은 무중단. 외부 호출은 타임아웃+fallback.
- **마일스톤 중간에 다음 마일스톤 작업을 당겨오지 않는다.** 범위 확장은 완성의 적.

## 기술 스택

| 영역 | 선택 |
|---|---|
| Backend | Spring Boot 3.x (Java 17, Gradle) |
| 수집 큐 | Kafka (**M6 도입** — 그전엔 단순 HTTP) |
| DB | PostgreSQL 단일 + PostGIS (+TimescaleDB **M6 전환**) |
| 캐시/윈도우 | Redis (L2 윈도우 상태, 알림 중복 억제) |
| 예측(L3) | Python 3.12 + FastAPI + scikit-learn |
| 실시간 | SSE |
| 인증 | JWT(화주) + 매직링크(수령기관) + 디바이스 키 + 어드민 키 |
| Frontend | React + 카카오/네이버 지도 |
| 배포 | 로컬 docker-compose |

## 시스템 아키텍처

3레이어: **L1 수집(readings 저장 + tracker_latest 낙관적 락 upsert) → L2 이상탐지(Redis 윈도우, z-score/이동평균, 급변vs점진 구분) → L3 예측(Python FastAPI 호출, 타임아웃 2s+fallback)**. 이벤트는 알림 파이프라인(Slack) + SSE로 전파.

- 동시성 사례: out-of-order 수신 시 tracker_latest lost update → 낙관적 락(version/timestamp guard)으로 방어. 원시 reading은 항상 저장.
- API 계약은 `docs/API_명세.md`가 단일 진실.

## 리포 구조 & 백엔드 패키지 구조

```
backend/    Spring Boot          frontend/   React
simulator/  Python 시뮬레이터     infra/      docker-compose, DB init SQL
prediction/ Python FastAPI 예측서버          docs/  API 명세
```

백엔드는 **도메인 기준 최상위 패키지** (레이어 기준 아님):

```
com.coldchain
├── tracker/      # 트래커·최신상태(tracker_latest)·디바이스 키
├── ingest/       # L1: 수집 API, 검증, (M6~) Kafka producer/consumer
├── reading/      # 시계열 저장·조회 (M6~ Timescale)
├── detection/    # L2: 윈도우, z-score, anomaly_event
├── prediction/   # L3: 예측서버 클라이언트, prediction 엔티티, 평가지표 집계
├── shipment/     # 배송, 화주-shipment-tracker-consignee 매핑, 경로/공간 쿼리(PostGIS)
├── alert/        # 알림 파이프라인 (Slack, 중복 억제, 재시도)
├── stream/       # SSE
├── auth/         # JWT, 매직링크, 어드민 키, 인가 스코핑
└── common/       # 에러(ProblemDetail), 설정, 공용 유틸
```

- 각 도메인 패키지 내부: `controller / service / repository / domain(entity) / dto` — 솔로 프로젝트이므로 헥사고날 등 과한 계층 금지.
- 도메인 간 참조는 service 레이어를 통해서만. 이벤트성 결합(탐지→알림 등)은 Spring `ApplicationEvent`로 느슨하게.

## 코딩 컨벤션 / API 규약

전체 계약은 **`docs/API_명세.md`가 단일 진실**. 요약:

- Base `/api/v1`, JSON camelCase, 리소스 복수형, enum `UPPER_SNAKE`.
- 에러는 **RFC 7807** (`application/problem+json`, Spring `ProblemDetail` + 확장 `code`/`timestamp`). 커스텀 envelope 금지.
- 시각은 ISO-8601 UTC, 자바 내부는 `Instant`. 좌표는 `lat`/`lon` (GeoJSON 응답만 `[lon, lat]`).
- 스코프 위반은 403이 아니라 **404** (존재 은닉).
- 수집 API는 `202 Accepted` (다운스트림 비동기 계약 — M6 Kafka 전환에도 불변).
- 테스트: 도메인 로직 단위 테스트 + 인가 스코핑은 반드시 테스트로 증명. Testcontainers로 PG/PostGIS 통합 테스트.
- 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:` …), 마일스톤 태그 `m0`, `m1` ….

## 로컬 실행 / 빌드 / 테스트 (M0에서 확정, 목표 계약)

```bash
docker compose -f infra/docker-compose.yml up -d   # PG(+PostGIS)·Redis·(M6~)Kafka
cd backend && ./gradlew bootRun
cd backend && ./gradlew test
cd prediction && uvicorn app.main:app --port 8000
cd frontend && npm run dev
```

완료 기준(M0): `docker compose up` 한 방에 빈 시스템이 뜬다. 실제 명령이 달라지면 **이 섹션을 즉시 갱신**한다.

## 시뮬레이터 구동 방식 (M1에서 확정, 목표 계약)

```bash
cd simulator
python run.py --trackers 50 --interval 5 --profile normal --target http://localhost:8080
# --profile: normal | gradual-rise | sudden-failure (냉동기 고장) — 뉴턴 냉각 + 노이즈 + 이산 이벤트
# --route: 사전 정의 경로 CSV (성남→서울대병원 기본) — GPS 좌표 재생
```

- 온도 곡선은 물리 기반(뉴턴 냉각법칙)이어야 함 — 예측(FR-5) 검증의 전제이므로 임의 직선 금지.
- 부하테스트(M6): `--trackers 500|1000|5000`으로 동일 시뮬레이터 재사용.

## 마일스톤 (현재: M0)

| # | 목표 | 완료 기준 |
|---|---|---|
| M0 | 기반 공사 — 리포·DB 스키마 v1·docker-compose·CI | `docker compose up` 한 방에 빈 시스템 기동 |
| M1 | 최소 파이프라인 — 시뮬레이터→수집→저장→조회 | 시뮬레이터 50개 데이터가 DB에 쌓이고 API로 조회 |
| M2 | 실시간 대시보드 — SSE·지도·온도 차트 | 지도 마커가 움직이고 차트가 실시간으로 흐름 |
| M3 | 이상탐지(L2) — 임계+통계 탐지·Slack 알림 | 급상승 시나리오에 Slack 경고 도착 |
| M4 | 예측(L3) ★간판 — 선형회귀 예측·선제 경고·평가지표 | 예측 성능이 리드타임·오탐률 수치로 측정됨 |
| M5 | 역할 멀티뷰 — JWT·매직링크·인가 스코핑 테스트 | 같은 배송을 역할별 다른 화면·범위로 조회 |
| M6 | 스케일 — 부하테스트→Kafka·Timescale 전환 | 개선 전/후 수치 비교 리포트 |
| M7 | 예측 심화 — 다변량·평가 자동화 | v1 vs v2 모델 수치 비교 |

- 각 마일스톤 끝에 README·데모 GIF 갱신. 막힌 지점·해결 과정은 그때그때 `docs/`에 기록.
