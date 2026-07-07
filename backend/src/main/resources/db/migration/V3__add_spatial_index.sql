-- PostGIS 공간 쿼리 첫 실사용(GET /trackers/{id}/track) 시점에 맞춰 추가.
-- 지금 당장의 조회 패턴(트래커별 시간순 정렬)은 V1의 btree 인덱스(tracker_id, recorded_at)로
-- 충분하다 — 이 GiST 인덱스는 향후 반경/뷰포트 기반 공간 쿼리(ST_DWithin, bounding box 등)를
-- 위한 선제 준비다.
CREATE INDEX idx_reading_position_gist ON reading USING GIST (position);
