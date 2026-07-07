-- M0에서 anomaly_event 테이블 뼈대(type/severity/message/z_score)만 미리 만들어뒀음.
-- M3에서 실제로 값을 채우며 발견한 요구사항: 같은 (tracker, type) 이상이 리딩마다
-- 반복 감지되는 걸 억제하고, 해소되면 명시적으로 닫아야 한다 — 그 활성/해제 생명주기를 추가한다.
ALTER TABLE anomaly_event
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLEARED')),
    ADD COLUMN clean_streak INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN cleared_at   TIMESTAMPTZ;

-- 트래커·유형별로 활성 이상은 동시에 최대 1건 — 감지 리스너의 동시 실행이 중복 ACTIVE 행을
-- 만들지 못하게 DB 레벨에서도 보장한다(shipment.tracker_id의 부분 유니크 인덱스와 같은 계열).
CREATE UNIQUE INDEX uq_anomaly_event_active ON anomaly_event (tracker_id, type) WHERE status = 'ACTIVE';
