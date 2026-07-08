-- M0에서 alert 테이블을 event_type/event_id 제네릭 참조 구조로 미리 만들어뒀지만, 실제
-- 알림 탭 요구사항(트래커별 조회, 발송 원문 미리보기, 재시도 이력)엔 트래커 직접 참조와
-- 원문 메시지 컬럼이 필요하다 — 기존 스키마를 확장한다.
-- 이 테이블은 지금까지 한 번도 쓰인 적 없어(M0 스켈레톤) 기존 행이 없다 — NOT NULL을
-- 바로 붙여도 안전하다(엔티티의 nullable=false와 스키마를 일치시킴).
ALTER TABLE alert
    ADD COLUMN tracker_id            VARCHAR(32)    NOT NULL REFERENCES tracker (id),
    ADD COLUMN severity              VARCHAR(20)    NOT NULL,
    ADD COLUMN temperature_at_event  NUMERIC(5, 2),
    ADD COLUMN message                TEXT          NOT NULL;

-- BREACH 알림은 event_id로 참조할 원본 이벤트 행이 없다(anomaly_event처럼 별도 테이블에
-- 저장되지 않음) — NULL 허용으로 완화. event_id는 현재 항상 NULL로 남는다(드릴다운 기능은
-- 요구되지 않음, 필요해지면 그때 채운다).
ALTER TABLE alert ALTER COLUMN event_id DROP NOT NULL;

ALTER TABLE alert ADD CONSTRAINT chk_alert_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'));

CREATE INDEX idx_alert_tracker_id ON alert (tracker_id, created_at DESC);
