-- GET /summary의 avgDeliveryMinutes 계산에 필요 — DELIVERED 전이 시각을 기록한다.
ALTER TABLE shipment ADD COLUMN delivered_at TIMESTAMPTZ;
