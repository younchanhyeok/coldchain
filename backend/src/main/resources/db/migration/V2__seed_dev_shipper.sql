-- M1 임시 조치: 인증(JWT)이 없는 상태에서 화주 소유권을 채우기 위한 단일 dev shipper.
-- id를 1로 고정해 애플리케이션 코드(DevShipperProvider)에서 상수로 참조한다.
-- M5에서 실제 로그인/JWT가 도입되면 이 시드 대신 인증된 principal의 shipper_id를 사용한다.
INSERT INTO app_user (id, email, password_hash, company_name, role)
VALUES (1, 'dev-shipper@coldchain.local', 'unused', '개발용 화주', 'SHIPPER');

-- 이후 시퀀스로 생성되는 id가 위에서 명시적으로 넣은 1과 충돌하지 않도록 진행시킨다.
SELECT setval('app_user_id_seq', 1, true);
