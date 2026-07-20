-- M6 PR4: reading을 TimescaleDB hypertable로 전환 (NFR-4).
-- V1 설계 시점에 예고된 ALTER — hypertable은 파티션 컬럼(recorded_at)이 PK를 포함한 모든
-- 유니크 제약에 들어가야 하므로 단일 id PK를 (id, recorded_at) 복합키로 교체한다.
-- V10의 UNIQUE(tracker_id, recorded_at)는 이미 recorded_at을 포함해 그대로 호환.
-- FK는 hypertable→일반 테이블(reading→tracker) 방향만 존재 — Timescale이 허용하는 방향.

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- IF EXISTS: 이 파일은 비트랜잭션(.conf 참조)이라 문장 사이에서 중단되면 부분 적용 상태가
-- 남는다 — DROP이 이미 커밋된 뒤 재실행돼도 첫 문장이 막히지 않게 재실행 안전으로 만든다.
ALTER TABLE reading DROP CONSTRAINT IF EXISTS reading_pkey;
ALTER TABLE reading ADD CONSTRAINT reading_pkey PRIMARY KEY (id, recorded_at);

-- chunk 1일: 로컬 개발·단시간 부하테스트(수십 분) 기준. 주의 — 5s 주기 지속 유입이면
-- 1,000트래커=17M행/일, 5,000트래커=86M행/일로 chunk가 커진다(행+인덱스가 메모리 예산을
-- 넘기면 insert 성능 저하). 장시간 sustained/대용량 시나리오에선 1~4시간으로 줄이는 것을
-- 검토(set_chunk_time_interval은 새 chunk부터 적용). migrate_data는 기존 dev 데이터 이관용.
SELECT create_hypertable('reading', 'recorded_at',
    chunk_time_interval => INTERVAL '1 day', migrate_data => true);
