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
| 어드민(평가지표 API 1개) | 정적 API 키 (화면은 v2) | `X-Admin-Key: {key}` |

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
| 예측 상태 `predictionStatus` | `ACTIVE` / `CANCELED` (추세 완화 취소) / `INVALIDATED` (급변 이벤트로 무효화) / `EXPIRED` |
| 알림 채널 | `SLACK` / `SSE` / `SMS`(목업) |
| 알림 유형 `alertType` | `BREACH` (임계 이탈) / `ANOMALY` (L2 이상탐지) |
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

- **202인 이유:** 수신 확인만 보장, 다운스트림(탐지·예측)은 비동기. M6 Kafka 전환 시에도 계약 불변 — HTTP 동기 저장(M1)→큐 발행(M6)으로 내부만 바뀜.
- `seq`: 디바이스 단조증가 시퀀스(선택). out-of-order·중복 판정 보조.
- 최신상태(tracker_latest) upsert는 `recordedAt`(+version) guard 낙관적 락 — 과거 데이터가 늦게 도착하면 원시 reading은 저장하되 최신상태는 갱신하지 않음(이 경우도 202. 409는 upsert 충돌 재시도 소진 시).
- 배치 전송(선택, M6): 같은 URL에 배열 body 허용 → `207`은 쓰지 않고 `{accepted, rejected[]}` 요약 반환.

에러: 401(키 불일치), 404(미등록 트래커), 422(온도 범위 -90~+60℃ 밖, 미래 recordedAt >5m).

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
      "activePrediction": { "predictedBreachAt": "2026-07-05T03:27:00Z", "leadTimeMinutes": 14 }
    }
  ],
  "page": 0, "size": 20, "totalElements": 57
}
```
정렬: `RISK`/`BREACH` 우선(예측 이탈 임박 순) → 나머지 최신 보고 순. UIUX.png "위험 화물 리스트" 요구 반영.

### GET /api/v1/trackers/{trackerId} — 단건 상세
목록 항목 + `shipment` 요약(출발/도착지, 수령기관명, 상태) + `activeAnomalies[]`.

### GET /api/v1/trackers/{trackerId}/readings — 온도 시계열 (차트)
쿼리: `from`/`to`(기본 최근 6h), `limit`, `interval`(선택 — `1m`/`5m` 다운샘플, M6 Timescale 이후).
```json
// 200
{
  "trackerId": "TRK-0001",
  "readings": [ { "ts": "2026-07-05T03:12:40Z", "temperature": 5.8, "lat": 37.4979, "lon": 127.0276 } ],
  "nextBefore": "2026-07-05T01:00:00Z"
}
```

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

### GET /api/v1/trackers/{trackerId}/prediction — 현재 예측 (FR-5 ★핵심)
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
  "forecast": [ { "ts": "2026-07-05T03:15:00Z", "temperature": 6.5 } ]
}
// 200 — 예측 없음(안전)
{ "status": "NONE" }
// 503 — 예측 서버 장애 (NFR-3): Problem Details + code=PREDICTION_UNAVAILABLE
//        단, 대시보드 UX를 위해 GET은 503 대신 { "status": "UNAVAILABLE" } 반환 (수집·조회는 무중단 원칙)
```
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
      "deliveredAt": null
    }
  ],
  "page": 0, "size": 20, "totalElements": 128
}
```
`trackerStatus`는 트래커 재사용과 무관하게 항상 그 트래커의 **현재** 최신 상태를 담는다 — `shipmentStatus=DELIVERED`인 화물은 화면에서 "완료" 뱃지를 우선 표시하고 `trackerStatus`는 참고용으로만 쓴다(트래커가 이미 다음 배송에 재배치됐을 수 있어서).

### GET /api/v1/summary — 화주 요약 통계 (FR-8 화주 뷰)
```json
// 200
{ "totalShipments": 128, "inTransit": 57, "breachCount": 3, "deliveredCount": 68, "rescuedByPrediction": 0, "avgDeliveryMinutes": 342 }
```
`breachCount`는 진행 중(IN_TRANSIT) 배송 중 현재 BREACH 상태인 건수(스냅샷). `deliveredCount`(M3, 화물 관리 KPI "배송 완료"의 데이터 원천)는 DELIVERED 건수. `avgDeliveryMinutes`는 DELIVERED 건의 (배송완료시각 − 생성시각) 평균(분), 완료 건이 없으면 `null`. `rescuedByPrediction`: 예측 경고 후 임계 미도달로 종료된 건수 — M3엔 예측이 없어 항상 `0`(생략이 아니라 정직한 값, M4에서 실제 집계로 교체) — 데모 헤드라인 수치.

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
| `reading` | trackerId, temperature, lat, lon, ts, status | 새 측정값 (트래커당 최대 1건/2s로 스로틀¹) |
| `anomaly` | anomalies 항목 + trackerId | L2 감지 — 활성화(`status=ACTIVE`)·해제(`status=CLEARED`) 전이 시에만 발행(활성 유지 중엔 반복 발행 안 함, M3~) |
| `prediction` | prediction 응답과 동일 + trackerId | 예측 생성/갱신/취소/무효화 (M4~) |
| `breach` | trackerId, temperature, thresholdTemp, ts | FR-6 임계 이탈 (정상→초과 전이 시 1회만 발행) |
| `heartbeat` | serverTs | 15s 간격 (연결 유지) |

¹ 트래커당 스로틀은 M6 부하테스트 시점에 구현한다(현재는 미적용 — M2는 트래커 수가 적어 필요를 겪지 않음). `anomaly`/`prediction`은 각각 M3/M4에서 그 데이터가 생기기 전까지 발행하지 않는다.

재연결: 표준 `Last-Event-ID` 지원은 v2. MVP는 재연결 시 REST 초기 로드로 복구(계약에 명시).

### 5.2 내부: Spring → Python 예측 서버 (외부 비공개)
`POST http://prediction:8000/internal/v1/predict`
```json
// req
{
  "trackerId": "TRK-0001",
  "thresholdTemp": 8.0,
  "window": [ { "ts": "...", "temperature": 5.1 } ],   // 최근 N개 (기본 30)
  "context": { "ambientTemp": null, "remainingDistanceMeters": null }  // v2 다변량용, MVP는 null
}
// 200
{ "willBreach": true, "predictedBreachAt": "...", "confidence": 0.83, "slopePerMinute": 0.14, "modelVersion": "v1-linear" }
```
- 타임아웃 2s, 재시도 1회, 실패 시 circuit open → 예측 스킵하고 수집·탐지는 계속 (NFR-3).
- `context`를 처음부터 계약에 포함 → M7 다변량 확장 시 인터페이스 불변.

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
- 에러: 401 `MAGIC_LINK_EXPIRED`, 404(무효 토큰).
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

## 8. 어드민 API (D2 — MVP는 이 1개만)

### GET /api/v1/admin/metrics/prediction — 예측 평가 지표 (M4, FR-5)
인증: `X-Admin-Key`. 쿼리: `from`/`to`, `modelVersion`.
```json
// 200
{
  "modelVersion": "v1-linear",
  "period": { "from": "...", "to": "..." },
  "totalPredictions": 412,
  "truePositives": 118,        // 경고 후 실제 이탈
  "falsePositives": 34,        // 경고 후 미이탈 (취소 포함)
  "missedBreaches": 12,        // 경고 없이 이탈
  "falsePositiveRate": 0.224,
  "avgLeadTimeMinutes": 11.3,  // 이탈 몇 분 전에 경고했나
  "medianLeadTimeMinutes": 9.0
}
```
"정확도를 자랑하는 게 아니라 측정한다"의 API 표면. 어드민 화면(v2)과 M7 비교 리포트가 이 위에 얹힘.

---

## 9. v2/v3 예약 (명세 생략, 경로만 예약)

- `GET /api/v1/events` — 예측·이상·조치를 아우르는 통합 이벤트 이력 (FR-12, v3). M3에서 알림 발송 이력은 `GET /api/v1/alerts`(4절)로 먼저 구현됨 — 서로 다른 개념, 이름 유사성에 유의.
- `GET/PATCH /api/v1/trackers/{id}` 메타 수정, 트래커 목록 관리 확장 (FR-11)
- `GET /api/v1/admin/**` — 어드민 대시보드 (고객사 수·시스템 지표·모델 지표 화면)
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
| FR-8 | 인증 4종 중 2역할(§1.2) + 스코핑 규칙 + `GET /track/{token}` + `GET /summary` |
| FR-9 | `GET /stream` (SSE) + `GET /trackers` |
| FR-10 | `GET /trackers/{id}/track` |
| FR-11(v2 최소) | `POST /trackers`, `POST/PATCH /shipments`, `GET /shipments`(목록, M3) |
