# 콜드체인 — API 명세 v1 (MVP)

> 범위: MVP 전체(M0~M5). v2/v3 엔드포인트는 §9 목록만.
> FR/NFR ID는 내부 기능명세 기준. D1~D3은 스코프 결정 사항(공간 MVP 승격 / MVP 2역할 / 로컬 compose 배포).
> 이 문서가 구현의 계약(contract). 구현 중 변경 시 이 문서를 먼저 갱신한다.

---

## 1. 공통 규약

### 1.1 기본

| 항목 | 규약 |
|---|---|
| Base URL | `/api/v1` |
| 포맷 | JSON (`application/json; charset=utf-8`) |
| 네이밍 | JSON 필드 camelCase, 리소스 복수형(`/trackers`), enum 대문자 스네이크(`IN_TRANSIT`) |
| 시각 | ISO-8601 UTC (`2026-07-05T03:12:45Z`). 서버 내부는 `Instant` |
| 좌표 | WGS84. `lat`/`lon` 숫자 필드. 경로 응답만 GeoJSON |
| ID | 서버 생성 리소스는 long ID. 트래커는 디바이스 시리얼 문자열(`TRK-0001`) |
| 페이징 | 시계열: `from`/`to`/`limit`(기본 500, 최대 5000) + `nextBefore` 커서. 목록: `page`/`size` |

### 1.2 인증 (FR-8, D2 — MVP 2역할 + 디바이스 + 어드민 API)

| 주체 | 방식 | 전달 |
|---|---|---|
| 화주(SHIPPER) | JWT (Access 30m / Refresh 14d) | `Authorization: Bearer {jwt}` |
| 수령기관(CONSIGNEE) | 매직링크 토큰 (계정 없음, 단일 배송 스코프) | URL path `{token}` — §6 |
| 디바이스(시뮬레이터·ESP32) | 트래커별 디바이스 키 | `X-Device-Key: {key}` |
| 어드민(§8 API 2개 + `/admin` 화면 v1, 로컬 전용) | 정적 API 키 | `X-Admin-Key: {key}` |

- 인가 스코핑: 화주는 **자사 shipment에 매핑된 트래커만** 조회 가능(화주–shipment–tracker–consignee 매핑으로 강제). 위반 시 `404`(존재 은닉, `403` 아님).
- 매직링크 토큰: 발급 시 shipment 1건에 바인딩, 배송 완료 후 N일(기본 7일) 만료.

### 1.3 에러 shape — RFC 7807 Problem Details

모든 에러는 `application/problem+json`. Spring `ProblemDetail` 사용 + 확장 필드 `code`, `timestamp`.

```json
{
  "type": "https://coldchain.dev/errors/reading-out-of-order",
  "title": "Out-of-order reading rejected",
  "status": 409,
  "detail": "recordedAt 2026-07-05T03:10:00Z is older than latest 2026-07-05T03:12:00Z",
  "instance": "/api/v1/trackers/TRK-0001/readings",
  "code": "READING_OUT_OF_ORDER",
  "timestamp": "2026-07-05T03:12:45Z"
}
```

**공통 에러 코드:**

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 필드 검증 실패 (확장 필드 `errors: [{field, reason}]` 포함) |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료/서명 불일치 |
| 401 | `MAGIC_LINK_EXPIRED` | 매직링크 만료 |
| 404 | `RESOURCE_NOT_FOUND` | 없거나 권한 밖 (스코프 위반 포함) |
| 409 | `READING_OUT_OF_ORDER` | 최신상태 upsert 버전 충돌 (낙관적 락) — 원시 reading은 저장됨 |
| 409 | `DUPLICATE_RESOURCE` | 중복 생성 (트래커 시리얼 등) |
| 422 | `SEMANTIC_INVALID` | 형식은 맞으나 의미 오류 (예: 온도 -100℃) |
| 429 | `RATE_LIMITED` | 수집 API 유량 제한 |
| 503 | `PREDICTION_UNAVAILABLE` | 예측 서버 장애 — fallback 응답(§5.2) |

### 1.4 상태 enum

| enum | 값 |
|---|---|
| 트래커 상태 `status` | `SAFE` / `CAUTION` / `RISK` (예측 경고 활성) / `BREACH` (임계 이탈) |
| 배송 상태 `shipmentStatus` | `READY` / `IN_TRANSIT` / `DELIVERED` |
| 이상 유형 `anomalyType` | `SUDDEN` (급변) / `GRADUAL` (점진) |
| 이상 상태 `anomalyStatus` | `ACTIVE` / `CLEARED` (연속 3회 미해당 시 해제) |
| 예측 상태 `predictionStatus` | `ACTIVE` / `CANCELED` (추세 완화 취소, 연속 3회 미해당 필요) / `INVALIDATED` (급변 이벤트로 무효화) / `EXPIRED` (예상 시각 경과, 15분 유예) / `BREACHED` (적중 — 실제 이탈, M4) |
| 알림 채널 | `SLACK` / `SSE` / `SMS`(목업) |
| 알림 유형 `alertType` | `BREACH` (임계 이탈) / `ANOMALY` (L2 이상탐지) / `PREDICTION` (선제 경고, M4) / `PREDICTION_CANCELED` (예측 취소 통보, M4) |
| 알림 발송 상태 `alertStatus` | `PENDING` (저장됨, 발송 시도 전) / `SENT` / `FAILED` (최대 재시도 후 실패) |

---

## 2. 인증 API

### POST /api/v1/auth/login — 화주 로그인
```json
// req
{ "email": "shipper@pharma.co", "password": "..." }
// 200
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "role": "SHIPPER", "companyName": "한국제약" }
```
에러: 401 `UNAUTHORIZED` (이메일/비밀번호 불일치도 동일 코드 — 계정 존재 은닉).

### POST /api/v1/auth/refresh
```json
// req
{ "refreshToken": "eyJ..." }
// 200 — login과 동일 shape
```

---

## 3. 수집 API (L1 — FR-1)

### POST /api/v1/trackers/{trackerId}/readings
디바이스(시뮬레이터·ESP32)가 주기 전송. 인증: `X-Device-Key`.

```json
// req
{
  "temperature": 5.8,
  "lat": 37.4979,
  "lon": 127.0276,
  "recordedAt": "2026-07-05T03:12:40Z",
  "seq": 1042
}
// 202 Accepted
{ "accepted": true, "serverTs": "2026-07-05T03:12:45Z" }
```

- **202인 이유:** 수신 확인만 보장, 다운스트림(탐지·예측)은 비동기. M6 Kafka 전환에도 계약 불변 — HTTP 동기 저장(M1)→큐 발행(M6)으로 내부만 바뀜. 202가 보장하는 지점: kafka 모드(M6~ 기본)=브로커 영속, direct 모드(A/B 토글)=DB 저장.
- `seq`: 디바이스 단조증가 시퀀스(선택). out-of-order·중복 판정 보조. 같은 `(trackerId, recordedAt)` 재전송은 멱등 처리(V10 유니크 — at-least-once 재전달·클라이언트 재시도 흡수).
- 최신상태(tracker_latest) upsert는 `recordedAt`(+version) guard 낙관적 락 — 과거 데이터가 늦게 도착하면 원시 reading은 저장하되 최신상태는 갱신하지 않음(이 경우도 202). **409(upsert 충돌 재시도 소진)는 direct 모드 한정** — kafka 모드에선 파티션(key=trackerId)이 트래커별 쓰기를 직렬화해 충돌 자체가 없다.
- 배치 전송(M6~): 같은 URL에 **배열 body** 허용(최대 500건, 초과는 422). `207`은 쓰지 않고 202 + 요약 반환 — 요소별 실패는 `rejected[]`로 모으고 나머지는 저장하는 부분 성공. 원시 저장은 JDBC 배치, 최신상태 upsert는 배열 중 최신 `recordedAt` 1건으로 collapse.

```json
// req (배열 body) — 디바이스가 버퍼링한 리딩 묶음
[ { "temperature": 5.8, "lat": 37.49, "lon": 127.02, "recordedAt": "...", "seq": 1041 }, ... ]
// 202
{ "accepted": 48, "rejected": [ { "index": 3, "code": "SEMANTIC_INVALID", "reason": "온도는 -90~60도 범위여야 합니다: 200.0" } ], "serverTs": "..." }
```

에러: 401(키 불일치), 404(미등록 트래커), 422(온도 범위 -90~+60℃ 밖, 미래 recordedAt >5m, 과거 recordedAt >7d — 시계 리셋 디바이스의 epoch급 timestamp가 hypertable chunk를 날짜당 생성하는 것 방지, M6~), 503(kafka 모드에서 브로커 발행 실패 — 저장 보장 없이 202를 주지 않는다, `INGEST_UNAVAILABLE`).

---

## 4. 조회 API (화주 JWT)

모든 응답은 자사 스코프로 자동 필터링.

> 기능명세서 FR-8의 `GET /me/trackers`는 별도 경로 대신 **스코핑된 `GET /trackers`로 통합** — 역할에 따라 응답 범위가 달라진다는 의미는 인가 필터로 구현하고, URL은 리소스 중심으로 유지.

### GET /api/v1/trackers — 활성 트래커 목록 + 최신 상태 (FR-9 대시보드 초기 로드)
쿼리: `status`(선택), `shipmentStatus`(기본 `IN_TRANSIT`), `page`/`size`.
```json
// 200
{
  "content": [
    {
      "trackerId": "TRK-0001",
      "shipmentId": 31,
      "productName": "백신 A (2-8℃)",
      "originName": "성남 물류센터",
      "destinationName": "서울대병원 약제부",
      "thresholdTemp": 8.0,
      "status": "RISK",
      "lastTemperature": 6.2,
      "lastPosition": { "lat": 37.4979, "lon": 127.0276 },
      "lastReportedAt": "2026-07-05T03:12:40Z",
      "activePrediction": { "predictedBreachAt": "2026-07-05T03:27:00Z", "leadTimeMinutes": 14, "slopePerMinute": 0.14 }
    }
  ],
  "page": 0, "size": 20, "totalElements": 57
}
```
`activePrediction`(M4)은 활성 예측이 없으면 `null` — `leadTimeMinutes`는 조회 시점부터 남은 분. 정렬은 `lastReportedAt` 내림차순(서버)이고, 위험 모니터링 탭의 "예상 이탈 임박순"은 이 필드로 **프론트에서** 정렬한다(신규 목록 API 없이 성립).

### GET /api/v1/trackers/{trackerId} — 단건 상세
목록 항목 + `shipment` 요약(출발/도착지 좌표, 수령기관명, **기사 연락처 driverContact**(M3), 상태) + `activeAnomalies[]`. 기사는 이름 필드가 없어 연락처로만 표시한다.

### GET /api/v1/trackers/{trackerId}/readings — 온도 시계열 (차트)
쿼리: `from`/`to`(기본 최근 6h, **반개구간 `[from, to)`** — 원시·다운샘플 동일), `limit`, `interval`(선택 — `1m`/`5m` 다운샘플, 미지원 값은 422). `limit`을 꽉 채우면 `nextBefore`(가장 오래된 항목 ts)가 오고, `to=nextBefore`로 다음 페이지를 요청하면 경계 중복 없이 이어진다.
```json
// 200 (원시 — interval 없음)
{
  "trackerId": "TRK-0001",
  "readings": [ { "ts": "2026-07-05T03:12:40Z", "temperature": 5.8, "minTemperature": null, "maxTemperature": null, "lat": 37.4979, "lon": 127.0276 } ],
  "nextBefore": "2026-07-05T01:00:00Z"
}
```
- `interval=1m|5m`(M6~): continuous aggregate 다운샘플. `ts`는 버킷 시작, `temperature`는 버킷 평균, `minTemperature`/`maxTemperature`는 버킷 내 최저/최고(원시 조회에선 `null`). 콜드체인에선 평균이 짧은 이탈을 가리므로 `maxTemperature`가 안전 신호다. `lat`/`lon`은 버킷 내 마지막 위치. 실시간 집계라 아직 굳지 않은 최신 버킷도 즉석 계산돼 포함된다.
- 보존 정책(M6~): 원시 8일 / 1분 집계 30일 / 5분 집계 180일. 8일 넘은 구간은 원시 조회가 비고 `interval` 다운샘플로만 조회된다.

### GET /api/v1/trackers/{trackerId}/anomalies — 이상 이벤트 (FR-4)
쿼리: `from`/`to`(기본 최근 6h), `type`.
```json
// 200
{
  "anomalies": [
    { "id": 88, "ts": "2026-07-05T03:10:00Z", "type": "SUDDEN", "severity": "HIGH", "message": "직전 대비 +23.78℃/분 변화 (z=4.2)", "zScore": 4.2, "status": "ACTIVE" }
  ]
}
```
같은 `(trackerId, type)` 조합의 활성 이상은 동시에 최대 1건(`status=ACTIVE`) — 리딩마다 반복 감지돼도 새 행을 만들지 않고, 조건이 연속 3회 미해당이면 `CLEARED`로 닫힌다(`clearedAt` 스탬프). CAUTION 트래커 상태(`GET /trackers`)는 유형 무관하게 `ACTIVE` 이상이 하나라도 있으면 켜진다.

### GET /api/v1/trackers/{trackerId}/prediction — 현재 예측 (FR-5 ★핵심, M4)
```json
// 200 — 활성 예측 있음
{
  "status": "ACTIVE",
  "predictedBreachAt": "2026-07-05T03:27:00Z",
  "leadTimeMinutes": 14,
  "thresholdTemp": 8.0,
  "slopePerMinute": 0.14,
  "modelVersion": "v1-linear",
  "createdAt": "2026-07-05T03:13:00Z",
  "forecast": [
    { "ts": "2026-07-05T03:13:00Z", "temperature": 6.06 },
    { "ts": "2026-07-05T03:27:00Z", "temperature": 8.0 }
  ]
}
// 200 — 예측 없음(트래커에 예측 에피소드가 한 번도 없었음)
{ "status": "NONE" }
```
`status`는 트래커의 **가장 최근 예측 에피소드** 상태를 그대로 보여준다 — `ACTIVE`뿐 아니라 `CANCELED`(추세 완화로 취소)·`INVALIDATED`(급변 감지로 무효화)·`EXPIRED`(예상 시각을 넘기고도 미이탈)·`BREACHED`(적중)도 나올 수 있다("안전한 실패 설계"가 실제로 화면에 보이는 지점). `leadTimeMinutes`(조회 시점부터 남은 분)와 `forecast`(anchor 실측점→임계 도달점 2점 직선 — 선형회귀라 두 점이면 충분)는 **`ACTIVE`일 때만** 채워지고 그 외 상태는 `null`/`[]`다.

이 엔드포인트는 **저장된 상태만 읽는다 — Python을 호출하지 않는다.** 예측 시도 자체는 리딩이 들어올 때(수집 파이프라인 안에서)만 일어나므로, "예측 서버 장애로 조회가 503"이라는 경우가 이 엔드포인트에서는 발생하지 않는다. Python 장애 시의 영향은 "새 예측이 갱신되지 않고 마지막 저장 상태가 계속 보이는 것"뿐이며, 수집·탐지·이 조회 전부 무중단이다(NFR-3).

`leadTimeMinutes`는 두 가지 다른 의미를 혼동하지 않는다 — 이 응답의 값은 "지금부터 남은 분"(화면의 "N분 후 이탈")이고, 평가지표(`GET /admin/metrics/prediction`, M4 PR3)의 리드타임은 "최초 경고가 실제 이탈보다 얼마나 앞섰나"(`breachedAt - createdAt`)로 별개다.

### 내부: Spring → Python 예측 서버 연동 실패 시 동작 (NFR-3)
Python 예측 서버 호출은 타임아웃 2s·재시도 1회 후에도 실패하면 30초 쿨다운(서킷 오픈) 동안 호출 자체를 생략한다 — 이 리딩은 예측 판정 없이 스킵되고, 수집·저장·L2 이상탐지에는 영향이 없다. 급변(SUDDEN) 이상 감지 시에는 활성 예측을 `INVALIDATED`로 전환한다(선형 추세 가정이 깨졌으므로).
`forecast[]`: 실측 실선→예측 점선 차트용 (UIUX.png). `INVALIDATED`: 급변 이벤트 감지 시 예측 무효화 — "안전한 실패 설계"의 API 표면.

### GET /api/v1/trackers/{trackerId}/track — 이동 경로 + 남은 거리 (FR-10, D1)
```json
// 200
{
  "trackerId": "TRK-0001",
  "path": { "type": "LineString", "coordinates": [ [127.0276, 37.4979], [127.0301, 37.5012] ] },
  "current": { "lat": 37.5012, "lon": 127.0301 },
  "destination": { "lat": 37.5665, "lon": 126.9780, "name": "서울대병원 약제부" },
  "remainingDistanceMeters": 8420,
  "etaMinutes": 12.4,
  "breachSegments": [
    { "type": "LineString", "coordinates": [ [127.0288, 37.4990], [127.0290, 37.4995] ] }
  ]
}
```
`path`는 이번 배송(진행 중 shipment) 시작 이후 리딩을 시간순으로 조립하며 **최근 500개 좌표로 제한**한다(무한정 누적 방지). `remainingDistanceMeters`는 `ST_Distance`(geography, 지구 곡률 반영 실거리). GeoJSON 좌표 순서는 표준대로 `[lon, lat]` — 단독 좌표 객체(`lat`/`lon` 필드)와 순서가 다름에 주의. `current`는 SSE 미연결 등 폴백용이며, 대시보드는 평소 `GET /stream`의 실시간 `lastPosition`을 우선 사용한다. `breachSegments`는 개별 초과 지점이 아니라 **연속으로 임계 초과였던 구간**을 GeoJSON LineString으로 묶은 목록이다 — 정상 리딩이 하나라도 끼면 구간이 끊긴다(지도에서 점이 아니라 구간 강조로 표시하기 위함). `etaMinutes`(M3, "도착 예상")는 **최근 5개 리딩의 이동거리 ÷ 경과시간으로 낸 평균속도 기반 근사치이며 실제 도로 경로 ETA가 아니다** — 정지 중이거나 리딩이 부족하면 `null`("계산 불가"). M4 예측("N분 후 이탈")과는 다른 개념이니 라벨·의미를 혼동하지 말 것.

### GET /api/v1/shipments — 화물 목록 (M3, 화물 관리 탭)
쿼리: `page`/`size`(기본 0/20). 검색·상태 필터는 프론트 클라이언트 사이드(화면_탭_구성.md).
```json
// 200
{
  "content": [
    {
      "shipmentId": 31,
      "trackerId": "TRK-0001",
      "productName": "백신 A",
      "originName": "성남 물류센터",
      "destinationName": "서울대병원 약제부",
      "shipmentStatus": "IN_TRANSIT",
      "trackerStatus": "CAUTION",
      "thresholdTemp": 8.0,
      "lastTemperature": 6.2,
      "lastPosition": { "lat": 37.4979, "lon": 127.0276 },
      "lastReportedAt": "2026-07-08T03:12:00Z",
      "createdAt": "2026-07-08T01:00:00Z",
      "inTransitAt": "2026-07-08T01:05:00Z",
      "deliveredAt": null
    }
  ],
  "page": 0, "size": 20, "totalElements": 128
}
```
`trackerStatus`는 트래커 재사용과 무관하게 항상 그 트래커의 **현재** 최신 상태를 담는다 — `shipmentStatus=DELIVERED`인 화물은 화면에서 "완료" 뱃지를 우선 표시하고 `trackerStatus`는 참고용으로만 쓴다(트래커가 이미 다음 배송에 재배치됐을 수 있어서). `inTransitAt`(M3~)은 배송 현황 탭의 "출발" 이벤트 시각 — 이 값 없이 `IN_TRANSIT`이면(마이그레이션 이전 데이터) 이벤트 목록에서 생략한다.

### GET /api/v1/summary — 화주 요약 통계 (FR-8 화주 뷰)
```json
// 200
{ "totalShipments": 128, "inTransit": 57, "breachCount": 3, "deliveredCount": 68, "rescuedByPrediction": 5, "avgDeliveryMinutes": 342 }
```
`breachCount`는 진행 중(IN_TRANSIT) 배송 중 현재 BREACH 상태인 건수(스냅샷). `deliveredCount`(M3, 화물 관리 KPI "배송 완료"의 데이터 원천)는 DELIVERED 건수. `avgDeliveryMinutes`는 DELIVERED 건의 (출발시각 − 배송완료시각) 평균(분), 완료 건이 없으면 `null`. `rescuedByPrediction`(M4): 예측 경고 후 임계 미도달로 종료된 누적 건수(취소+만료) — "구조된 박스" 데모 헤드라인 수치. M5 이전이라 화주 스코핑 없이 시스템 전체 집계(다른 M3 API와 동일한 전제).

### GET /api/v1/alerts — 알림 발송 이력 (FR-6/7, M3)
쿼리: `trackerId`(선택, 없으면 자사 전체), `page`/`size`(기본 0/20). 알림 탭·화물 관리 상세 패널 공용.
```json
// 200
{
  "content": [
    {
      "id": 12,
      "trackerId": "TRK-0001",
      "type": "BREACH",
      "severity": "HIGH",
      "temperatureAtEvent": 9.2,
      "message": "[긴급] TRK-0001 온도 9.2℃ — 임계 8.0℃ 초과",
      "channel": "SLACK",
      "status": "SENT",
      "retryCount": 0,
      "createdAt": "2026-07-08T02:10:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```
`message`는 실제로 Slack에 발송된 payload 원문 그대로 — 알림 탭 "발송 원문 미리보기"가 이 값을 그대로 표시한다. `retryCount`는 최종 상태에 도달하기까지 시도한 재시도 횟수(최초 시도 제외, 최대 2). 저장 순서는 "PENDING 즉시 저장 → 발송 시도 → 최종 상태(SENT/FAILED) 갱신" — Slack이 완전히 죽어도 PENDING 행 자체는 남는다.
**FR-12의 `GET /api/v1/events`(9절 예약)와는 다른 엔드포인트다**: `/alerts`는 이번에 만든 alert 테이블(발송 이력) 전용이고, `/events`는 v3에서 예측·이상·조치를 아우르는 통합 이벤트 로그로 예약된 별도 개념 — 이름이 비슷해 보이지만 충돌하지 않는다.

---

## 5. 실시간 & 예측 내부 연동

### 5.1 GET /api/v1/stream — SSE (FR-9)
인증: JWT (쿼리 `?token=` 허용 — EventSource가 헤더 미지원). 화주 스코프 이벤트만 수신.

| event | data (요약) | 발생 |
|---|---|---|
| `reading` | trackerId, temperature, lat, lon, ts, status | 새 측정값 (conflation¹ — 트래커당 최신값만 1s 주기 flush) |
| `anomaly` | anomalies 항목 + trackerId | L2 감지 — 활성화(`status=ACTIVE`)·해제(`status=CLEARED`) 전이 시에만 발행(활성 유지 중엔 반복 발행 안 함, M3~) |
| `prediction` | trackerId, status, predictedBreachAt, slopePerMinute, modelVersion, createdAt | 에피소드 생성(ACTIVE)·취소·무효화·만료·적중 전이 시에만 발행(리딩마다의 단순 갱신엔 발행 안 함) — forecast/leadTimeMinutes는 안 담고 조회 시 `GET /prediction`이 재계산(M4~) |
| `breach` | trackerId, temperature, thresholdTemp, ts | FR-6 임계 이탈 (정상→초과 전이 시 1회만 발행) |
| `alert` | id, trackerId, type, severity, status(SENT/FAILED), createdAt | FR-7 알림 최종 상태 확정 시 1회(알림 탭 Live 배지용 — 본문은 담지 않고 GET /alerts로 조회, M3~) |
| `heartbeat` | serverTs | 15s 간격 (연결 유지) |

¹ M6 부하테스트에서 이벤트당 즉시 send가 커넥션당 ~150건/s 캡 + 백로그 무한 누적(e2e 290초)으로 실측되어, 계획했던 "최소 간격 스로틀" 대신 **conflation**(트래커당 최신값만 남기고 1초 주기 일괄 송신)으로 구현 — 리딩 주기가 5s라 최소 간격 스로틀은 아무것도 거르지 못한다. 온도·위치는 최신값이 이전값을 대체하는 데이터라 중간값 생략은 정보 손실이 아니며, `breach` 등 상태 전이 이벤트는 conflation 없이 즉시 발행. `anomaly`/`prediction`은 각각 M3/M4에서 그 데이터가 생기기 전까지 발행하지 않는다.

재연결: 표준 `Last-Event-ID` 지원은 v2. MVP는 재연결 시 REST 초기 로드로 복구(계약에 명시).

### 5.2 내부: Spring → Python 예측 서버 (외부 비공개)
`POST http://prediction:8000/internal/v1/predict`
```json
// req
{
  "trackerId": "TRK-0001",
  "thresholdTemp": 8.0,
  "window": [ { "ts": "...", "temperature": 5.1, "ambientTemp": 24.0 } ],   // 최근 N개 (기본 30). ambientTemp: 외기 센서값(nullable)
  "context": { "ambientTemp": 24.0, "remainingDistanceMeters": 5000.0 }  // v2 다변량 입력(M7~). nullable — 없으면 v1 폴백
}
// 200
{ "willBreach": true, "predictedBreachAt": "...", "confidence": null, "slopePerMinute": 0.14, "modelVersion": "v1-linear" }
```
`confidence`는 v1(선형회귀)에서 **항상 null**이다 — 선형회귀는 확률을 산출하지 않으므로 신뢰도를 창작하지 않는다("위험도 96%·Confidence 97%" 표시 거부 원칙과 동일선). v2(뉴턴 냉각)도 확률 미산출이라 null 유지.
- 타임아웃 2s, 재시도 1회, 실패 시 circuit open → 예측 스킵하고 수집·탐지는 계속 (NFR-3).
- `context`(외기온·잔여거리)·per-point `ambientTemp`는 M7 PR1에서 실제 값 배선(M4까진 예약만). v1은 무시하고 v2-newton이 사용 — 계약은 M4 예약분 그대로라 인터페이스 불변. context/ambient 부재(구 디바이스) 시 모델 서버가 v1 경로로 폴백하고 `modelVersion`을 정직하게 보고.

---

## 6. 수령기관 API (매직링크 — FR-8)

계정·로그인 없음. 토큰이 곧 인가 스코프(shipment 1건).

### GET /api/v1/track/{token} — 단일 화물 트래킹 뷰 (모바일)
```json
// 200
{
  "shipment": { "productName": "백신 A", "shipperName": "한국제약", "status": "IN_TRANSIT", "eta": "2026-07-05T04:30:00Z" },
  "currentTemperature": 5.8,
  "temperatureStatus": "SAFE",
  "thresholdTemp": 8.0,
  "position": { "lat": 37.5012, "lon": 127.0301 },
  "remainingDistanceMeters": 8420,
  "temperatureLog": [ { "ts": "...", "temperature": 5.1 } ]
}
```
- 노출 범위 최소화: 화주 내부 통계·다른 배송·트래커ID 원문 비노출.
- `temperatureStatus`는 `SAFE`/`BREACH`/`UNKNOWN` 셋 중 하나 — 아직 리딩이 없으면(배송 생성 직후 등) `currentTemperature`가 null이고 이때 `UNKNOWN`을 반환한다. 데이터가 없는 상태를 `SAFE`로 단정하지 않는다(콜드체인 도메인에서 가장 나쁜 종류의 오독).
- **시간 창 제한(트래커 재사용 방어):** 트래커는 배송 완료 후 재사용되므로, 모든 필드는 이 shipment의 창 `[createdAt, deliveredAt]` 안의 데이터만 담는다 — 완료된 배송의 뷰는 "배송 당시 마지막 온도"를 유지하고(다음 배송 온도로 갱신되지 않음) `position`은 숨긴다(창 밖이면 이미 다음 배송의 위치). 반대로 재사용 트래커의 새 배송은 첫 리딩 전까지 이전 배송의 잔상 없이 `UNKNOWN`이다.
- 에러: 401 `MAGIC_LINK_EXPIRED`, 404(무효 토큰). 무효 토큰이 404인 것은 화주 스코프 위반과 같은 존재 은닉 원칙 — 만료(401)는 "정상 발급됐던 링크"라는 사실 자체가 수령기관에게 이미 알려져 있어 은닉 대상이 아니고, 갱신 요청 안내가 UX상 더 유용하다.
- 실시간: MVP는 30s 클라이언트 폴링 — 수령기관 뷰는 단일 화물 조회라 SSE 인프라를 확장할 이유가 없음(YAGNI).

---

## 7. 관리 API (M1 최소 CRUD)

### POST /api/v1/trackers — 트래커 등록 (화주)
```json
// req
{ "trackerId": "TRK-0001", "productName": "백신 A (2-8℃)", "thresholdTemp": 8.0 }
// 201 — Location: /api/v1/trackers/TRK-0001
{ "trackerId": "TRK-0001", "deviceKey": "dk_...", "createdAt": "..." }
```
`deviceKey`는 생성 시 1회만 노출. 409 `DUPLICATE_RESOURCE`.

### POST /api/v1/shipments — 배송 생성 + 트래커 바인딩 + 매직링크 발급 (화주)
```json
// req
{
  "trackerId": "TRK-0001",
  "productName": "백신 A (2-8℃)",
  "origin": { "lat": 37.4979, "lon": 127.0276, "name": "성남 물류센터" },
  "destination": { "lat": 37.5665, "lon": 126.9780, "name": "서울대병원 약제부" },
  "consignee": { "name": "서울대병원 약제부", "contact": "02-xxx" },
  "driverContact": "010-xxxx"   // FR-7 기사 알림 채널용 (MVP: Slack 멘션 문자열)
}
// 201
{ "shipmentId": 31, "magicLink": "https://.../t/mlk_9f2...", "status": "READY" }
```

### PATCH /api/v1/shipments/{id} — 상태 전이
```json
// req
{ "status": "IN_TRANSIT" }   // READY→IN_TRANSIT→DELIVERED만 허용, 위반 시 422
```

---

## 8. 어드민 API (D2 — 평가지표 + 시스템 집계 2개)

### GET /api/v1/admin/overview — 시스템 전체 집계 (M5, 관리자 뷰 v1)
인증: `X-Admin-Key`(metrics와 동일 — 미설정 시 어떤 키를 보내도 401).
```json
// 200
{ "shipperCount": 2, "activeTrackerCount": 9 }
```
`activeTrackerCount`는 `shipment.status != DELIVERED` 카운트 — 트래커당 비-DELIVERED shipment는 최대 1건(생성 시 강제)이므로 "배송 진행 중인 트래커 수"와 동치. 화주별 스코프 분리 없는 시스템 전체 집계이며, `/admin` 화면(v1)이 이 값과 아래 metrics를 함께 표시한다. **어드민 화면은 로컬 전용** — 프론트가 `VITE_ADMIN_KEY`를 번들에 포함하므로 공개 배포 불가(서버사이드 프록시/어드민 로그인은 v2).

### GET /api/v1/admin/metrics/prediction — 예측 평가 지표 (M4, FR-5)
인증: `X-Admin-Key`(미설정 시 어떤 키를 보내도 401 — 실수로 열린 채 배포되지 않게). 쿼리: `from`/`to`(필수), `modelVersion`(선택 — 생략 시 전체 모델 버전 합산).
```json
// 200
{
  "modelVersion": "v1-linear",
  "period": { "from": "...", "to": "..." },
  "totalPredictions": 412,
  "truePositives": 118,        // 경고 후 실제 이탈(BREACHED)
  "falsePositives": 34,        // 경고 후 미이탈(CANCELED+EXPIRED)
  "missedBreaches": 12,        // 활성 예측 없이 발생한 이탈 — 같은 트래커 10분 이내 재이탈은
                                // 경계 진동(flap)으로 보아 한 사건으로 묶는다
  "falsePositiveRate": 0.224,  // FP/(TP+FP)
  "hitRate": 0.776,            // 적중률 = TP/(TP+FP) — 통계학의 precision(정밀도)에 해당.
                                // recall(놓친 이탈까지 분모에 넣은 TP/(TP+missedBreaches))은
                                // 정의 안 함 — v2에서 필요해지면 추가(FR-5는 리드타임·오탐률·
                                // 적중률 3지표로 확정, Precision/Recall/F1 전환은 M7 재검토 대상).
  "avgLeadTimeMinutes": 11.3,  // breachedAt-createdAt 평균(이탈 몇 분 전에 최초 경고했나)
  "medianLeadTimeMinutes": 9.0,
  "episodes": [
    { "trackerId": "TRK-0001", "productName": "백신 A (gradual-rise)", "status": "BREACHED", "leadTimeMinutes": 12, "createdAt": "2026-07-05T03:13:00Z" }
  ]
}
```
`episodes[]`는 리포트 탭 "시나리오 결과 테이블"의 데이터원 — 기간 내 **생성**된 예측 에피소드 전부(`ACTIVE`·`INVALIDATED`도 포함). `createdAt`은 그 에피소드가 처음 경고된 시각(불변, `Prediction.createdAt` 그대로) — "언제 발생했나"의 기준이며 리드타임 계산의 앵커와 같은 값이다. **`episodes.length` = `totalPredictions` ≠ `truePositives + falsePositives`** — TP/FP/리드타임은 그중 **종결되고 적중 여부가 갈린 것**(`BREACHED`/`CANCELED`/`EXPIRED`)만 집계하고, 아직 `ACTIVE`인 것과 급변으로 `INVALIDATED`된 것은 진행 중이거나 판정 불가라 어느 쪽으로도 세지 않는다. "정확도를 자랑하는 게 아니라 측정한다"의 API 표면 — 어드민 화면(v2)과 M7 비교 리포트가 이 위에 얹힘.

같은 에피소드 집합(`CANCELED`+`EXPIRED`)을 이 지표에선 "오탐(falsePositives)"으로, `GET /summary`의 `rescuedByPrediction`("구조된 박스")에선 같은 수를 부른다 — 관점이 다를 뿐 계산은 동일하다: 개입(선제 경고) 없이 그 화물이 실제로 이탈했을지 반사실을 검증할 수 없으므로, "예측 덕에 구조됐다"는 인과를 주장하지 않고 "경고 후 이탈 없이 종료됐다"는 사실만 두 관점(오탐률 분모/구조 헤드라인)으로 보여준다.

---

## 9. v2/v3 예약 (명세 생략, 경로만 예약)

- `GET /api/v1/events` — 예측·이상·조치를 아우르는 통합 이벤트 이력 (FR-12, v3). M3에서 알림 발송 이력은 `GET /api/v1/alerts`(4절)로 먼저 구현됨 — 서로 다른 개념, 이름 유사성에 유의.
- `GET/PATCH /api/v1/trackers/{id}` 메타 수정, 트래커 목록 관리 확장 (FR-11)
- 어드민 대시보드 확장 — v1(§8 overview + `/admin` 화면, M5)에서 못 담은 것: 화주별 스코프 분리 집계, 시스템 전체 `rescuedByPrediction` 합산, 공개 배포 가능한 어드민 인증(서버사이드 프록시 또는 어드민 로그인)
- `GET /api/v1/shipments/{id}/report` — GDP 규제 리포트 내보내기
- SSE `Last-Event-ID` 재전송, 수령기관 SSE

---

## 부록 A. FR ↔ 엔드포인트 매핑

| FR | 엔드포인트 |
|---|---|
| FR-1 | `POST /trackers/{id}/readings` |
| FR-4 | `GET /trackers/{id}/anomalies` + SSE `anomaly` |
| FR-5 ★ | `GET /trackers/{id}/prediction` + SSE `prediction` + 내부 `/internal/v1/predict` + `GET /admin/metrics/prediction` |
| FR-6 | SSE `breach` + Slack + `GET /alerts` (M3) |
| FR-7 | Slack 웹훅 + `GET /alerts`(발송 이력·재시도, M3) |
| FR-8 | 인증 4종 중 2역할(§1.2) + 스코핑 규칙 + `GET /track/{token}` + `GET /summary` + `GET /admin/overview`(관리자 뷰 v1) |
| FR-9 | `GET /stream` (SSE) + `GET /trackers` |
| FR-10 | `GET /trackers/{id}/track` |
| FR-11(v2 최소) | `POST /trackers`, `POST/PATCH /shipments`, `GET /shipments`(목록, M3) |
