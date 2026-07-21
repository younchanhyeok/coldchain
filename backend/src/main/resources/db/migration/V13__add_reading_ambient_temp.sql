-- M7 PR1: 다변량 예측(v2)용 외기온 컬럼. 디바이스 외기 센서값을 리딩과 함께 저장한다
-- (v1은 무시, v2-newton이 뉴턴 냉각식의 ambient 항으로 사용). nullable — 구 데이터·외기온
-- 미탑재 디바이스는 null이고, 그 경우 예측은 v1 경로로 폴백한다(모델 서버가 판단).
-- hypertable에 nullable 컬럼 추가는 chunk 재작성 없이 안전(메타데이터 변경).
ALTER TABLE reading ADD COLUMN ambient_temp NUMERIC(5, 2) NULL;
