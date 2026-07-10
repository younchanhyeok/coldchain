-- M5: 로그인 도입 — dev shipper에 실제 BCrypt 비밀번호를 부여하고 데모 계정 형태로 정리.
-- 데모 비밀번호(README에도 기록): 화주A coldchain-a / 화주B coldchain-b
UPDATE app_user
SET email         = 'shipper-a@coldchain.local',
    company_name  = '한국제약',
    password_hash = '$2y$10$Ma5XjyFlJQndOEKl0YqTB.TL6pNUDXic0hCztfo.3GIvZva7Hq.bq'
WHERE id = 1;

-- 인가 스코핑 테스트·데모용 두 번째 화주 — A로 로그인하면 B의 화물이 보이지 않아야 한다(FR-8).
INSERT INTO app_user (id, email, password_hash, company_name, role)
VALUES (2, 'shipper-b@coldchain.local',
        '$2y$10$qMglpVY3I2E2MKYM3DhFwuc.xbbScHmH5r1ZVJUWcarnYVInCJqCi',
        '서울바이오', 'SHIPPER');

SELECT setval('app_user_id_seq', 2, true);
