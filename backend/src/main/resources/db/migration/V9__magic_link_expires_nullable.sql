-- M5: 매직링크는 발급 시점(READY/IN_TRANSIT)엔 만료가 없다 — 배송 완료(DELIVERED) 전이 시에만
-- delivered_at+7일로 세팅한다. 그 전까지 expires_at을 NULL로 둘 수 있어야 하므로 NOT NULL을 푼다.
ALTER TABLE magic_link_token ALTER COLUMN expires_at DROP NOT NULL;
