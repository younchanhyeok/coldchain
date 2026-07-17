-- M6 PR4: reading을 TimescaleDB hypertable로 전환 (NFR-4).
-- V1 설계 시점에 예고된 ALTER — hypertable은 파티션 컬럼(recorded_at)이 PK를 포함한 모든
-- 유니크 제약에 들어가야 하므로 단일 id PK를 (id, recorded_at) 복합키로 교체한다.
-- V10의 UNIQUE(tracker_id, recorded_at)는 이미 recorded_at을 포함해 그대로 호환.
-- FK는 hypertable→일반 테이블(reading→tracker) 방향만 존재 — Timescale이 허용하는 방향.

CREATE EXTENSION IF NOT EXISTS timescaledb;

ALTER TABLE reading DROP CONSTRAINT reading_pkey;
ALTER TABLE reading ADD CONSTRAINT reading_pkey PRIMARY KEY (id, recorded_at);

-- chunk 1일: 리딩 5s 주기 × 수천 트래커 기준 chunk당 수백만 행 수준 — 시계열 조회가
-- recorded_at 범위로 chunk 프루닝되는 단위. migrate_data는 기존 dev 데이터 이관용.
SELECT create_hypertable('reading', 'recorded_at',
    chunk_time_interval => INTERVAL '1 day', migrate_data => true);
