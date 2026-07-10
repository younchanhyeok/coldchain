# coldchain

콜드체인 IoT 관제 시스템 — 백엔드 포트폴리오 프로젝트.

## 개요

의약품·식품 등 콜드체인 배송은 온도가 임계값을 벗어난 **후에** 알아채면 이미 늦다. 이 프로젝트는 트래커(온도·위치) 데이터를 실시간 수집해 이상 징후를 탐지하는 것을 넘어, 현재 추세로부터 **N분 후 온도 이탈을 예측**해 문제가 벌어지기 전에 경고한다. 급변 이벤트(냉동기 고장 등)가 감지되면 예측은 즉시 무효화하고 알림으로 전환하는 안전한 실패 설계를 따른다.

## 데모

> `docs/demo.gif`는 아직 캡처 전 — M4(예측 점선·위험 모니터링·리포트 탭) 반영한 새 데모 GIF 촬영 필요.

시뮬레이터(gradual-rise + sudden-failure 트래커)를 돌리면 화주 대시보드·위험 모니터링 탭에서 예측 점선(실측 실선→예측 점선)이 실시간으로 그려지고, 급변 감지 시 `INVALIDATED` 배지로 즉시 전환되는 것을 확인할 수 있다.

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
| M0 | 기반 공사 — 리포·DB 스키마 v1·docker-compose·CI | `docker compose up` 한 방에 빈 시스템 기동 | 완료 |
| M1 | 최소 파이프라인 — 시뮬레이터→수집→저장→조회 | 시뮬레이터 50개 데이터가 DB에 쌓이고 API로 조회 | 완료 |
| M2 | 실시간 대시보드 — SSE·지도·온도 차트 | 지도 마커가 움직이고 차트가 실시간으로 흐름 | 완료 |
| M3 | 이상탐지(L2) — 임계+통계 탐지·Slack 알림 | 급상승 시나리오에 Slack 경고 도착 | 완료 |
| M4 | 예측(L3) ★핵심 — 선형회귀 예측·선제 경고·평가지표 | 예측 성능이 리드타임·오탐률 수치로 측정됨 | 완료 |
| M5 | 역할 멀티뷰 — JWT·매직링크·인가 스코핑 테스트 | 같은 배송을 역할별 다른 화면·범위로 조회 | 완료 |
| M6 | 스케일 — 부하테스트→Kafka·Timescale 전환 | 개선 전/후 수치 비교 리포트 | - |
| M7 | 예측 심화 — 다변량·평가 자동화 | v1 vs v2 모델 수치 비교 | - |

## 실행

```bash
docker compose -f infra/docker-compose.yml up -d   # PG(+PostGIS)·Redis·(M6~)Kafka
cd backend && JWT_SECRET=<32바이트 이상 임의 문자열> ADMIN_KEY=<임의 키> ./gradlew bootRun
cd prediction && uvicorn app.main:app --port 8000    # 최초 1회 pip install -r requirements.txt 필요
cd frontend && npm install && npm run dev            # .env.example 복사 — VITE_KAKAO_MAP_KEY(지도), VITE_ADMIN_KEY(리포트 탭·/admin, 백엔드 ADMIN_KEY와 동일값)
```

`JWT_SECRET`은 미설정이거나 32바이트 미만이면 백엔드가 **기동을 거부**한다(fail-fast — 랜덤 생성 폴백을 두면 재기동마다 전 토큰이 무효화되고 설정 누락이 은폐되므로 두지 않았다). `ADMIN_KEY`는 미설정이면 어드민 API가 어떤 키를 보내도 401.

### 데모 계정 (V8 시드)

| 역할 | 이메일 | 비밀번호 | 회사명 |
|---|---|---|---|
| 화주 A | `shipper-a@coldchain.local` | `coldchain-a` | 한국제약 |
| 화주 B | `shipper-b@coldchain.local` | `coldchain-b` | 서울바이오 |

### 역할 시나리오 — 같은 배송, 세 화면 (M5 완료 기준)

1. **화주**: `localhost:5173` 로그인(화주 A) → 대시보드에서 자사 트래커 전체 관제. 화주 B로 로그인하면 A의 화물이 보이지 않는다(스코프 위반은 403이 아니라 **404** — 존재 자체를 은닉, 통합 테스트로 부재/타사 404 응답 body가 구분 불가함까지 증명).
2. **수령기관**: 배송 생성(`POST /api/v1/shipments`) 응답의 `magicLink`(`/t/{token}`)를 모바일 뷰포트로 열면 계정 없이 **그 배송 1건만** — 현재 온도·상태 배지·지도·온도 로그·ETA. 트래커ID·전체 경로·이탈 구간은 응답 DTO에 필드 자체가 없다(직렬화 누락이 아니라 타입으로 비노출 보장).
3. **관리자**: `localhost:5173/admin` — 고객사 수·활성 트래커 수 + 예측 평가지표(시스템 전체 집계).

기사(드라이버)는 화면 없이 M3 Slack 알림 채널로 커버(FR-7).

## 보안 트레이드오프 (인지하고 선택한 것들)

- **토큰은 localStorage, refresh는 무상태(revocation 없음).** 헤더 방식이라 CSRF는 원천 차단되지만, XSS 한 번이면 스크립트가 14일짜리 refresh 토큰을 읽을 수 있고 서버에 폐기 수단이 없다 — 두 결정이 결합된 리스크임을 인지하고 선택했다(솔로 포트폴리오 규모에서 토큰 저장소+정리 잡+회전 탐지의 비용이 더 큼). 제대로 하려면: access는 메모리에만, refresh는 httpOnly 쿠키로 — 단 그러면 refresh 엔드포인트가 쿠키 기반이 되어 **CSRF 방어가 다시 필요해지는 연쇄**까지 감수해야 한다.
- **어드민 키가 프론트 번들에 새겨진다.** Vite의 `VITE_*`는 빌드 시점에 JS에 문자열로 포함되므로 `/admin` 화면을 포함한 프론트는 **로컬 데모 전용**이다(공개 배포하려면 서버사이드 프록시나 어드민 로그인 필요 — 어드민 화면 v2 이연의 근거).
- **로그인 브루트포스 방어(rate limit·계정 잠금) 없음.** 데모 계정 2개뿐인 로컬 환경 전제. 대신 로그인 실패는 사유(계정 없음/비밀번호 불일치) 불문 동일한 401 body로 계정 존재를 은닉한다(테스트로 body 동일성 증명).
- **매직링크 토큰은 `SecureRandom` 32바이트(base64url)** — 추측 불가 엔트로피. 무효 토큰은 404(존재 은닉), 만료는 401 `MAGIC_LINK_EXPIRED`(이미 정상 발급됐던 링크라 은닉 대상이 아니고, "화주에게 새 링크를 요청하라"는 안내가 UX상 유용).
- **리포트 탭 예측 지표는 시스템 전체 집계** — 화주별 스코프 분리는 v2(어드민 metrics API가 화주 축을 아직 갖지 않음, 화면에도 명시).

시뮬레이터(최초 1회 `pip install -r requirements.txt` 필요):

```bash
cd simulator
python run.py --trackers 50 --interval 5 --profile normal --target http://localhost:8080

# 정상 트래커 + 급변(sudden-failure) 트래커를 섞어 돌리면 대시보드에서 SAFE→BREACH 실시간 전이를 볼 수 있다
python run.py --trackers 40 --interval 5 --profile normal --target http://localhost:8080
python run.py --trackers 10 --interval 5 --profile sudden-failure --target http://localhost:8080

# M4 데모 두 갈래 — gradual-rise: 선제 경고(RISK·예측 점선)→실제 이탈(적중),
#                  sudden-failure: 급변 감지 시 예측 INVALIDATED + 즉시 알림 전환(안전한 실패)
python run.py --trackers 1 --interval 3 --profile gradual-rise --target http://localhost:8080
python run.py --trackers 1 --interval 3 --profile sudden-failure --target http://localhost:8080
```

API 계약은 [`docs/API_명세.md`](docs/API_명세.md) 참고.

## 컨벤션 · 깃 전략

상세는 [`CLAUDE.md`](CLAUDE.md) 참고. 요약:

- 브랜치: `main` 단일 + 기능 단위 `feat/...` → merge
- 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:` …)
- 마일스톤 완료 시 태그 `m0`~`m7`
