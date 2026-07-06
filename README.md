# coldchain

콜드체인 IoT 관제 시스템 — 온도 이탈을 **사전에 예측·경고**하는 실시간 데이터 파이프라인.

> "임계값을 넘은 뒤 알리는 게 아니라, 현재 추세로 N분 후 이탈을 예측해 골든타임을 확보한다."

- 3레이어: L1 수집 → L2 이상탐지 → L3 예측 (Spring Boot + PostgreSQL/PostGIS + Python FastAPI)
- 상태: **M0 기반 공사 진행 중** — 마일스톤별 상세는 추후 갱신

## 실행

```bash
docker compose -f infra/docker-compose.yml up -d
```

(M0 완료 시 갱신 예정)
