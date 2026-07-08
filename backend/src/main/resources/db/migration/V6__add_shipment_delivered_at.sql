-- GET /summary의 avgDeliveryMinutes 계산에 필요 — 실제 "배송 소요시간"(출발→도착)을 재려면
-- 생성(주문) 시각이 아니라 IN_TRANSIT 전이 시각이 기준점이어야 한다. created_at만 썼다면
-- 출발 전 대기시간까지 "배송시간"에 섞여 들어간다.
ALTER TABLE shipment
    ADD COLUMN in_transit_at TIMESTAMPTZ,
    ADD COLUMN delivered_at  TIMESTAMPTZ;
