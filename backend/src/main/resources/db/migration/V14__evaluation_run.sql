-- M7 PR3: 평가 런 — 예측 지표의 시점 스냅샷. M4의 온디맨드 계산(getMetrics)을 "런" 단위로
-- 상시 기록해 v1 vs v2 비교와 시간 경과 추이를 남긴다. 지표 수학은 getMetrics 재사용(중복 없음),
-- 이 테이블은 스냅샷(기간·모델버전·라벨이라는 런 정체성)만 고정한다.
CREATE TABLE evaluation_run (
    id                              BIGSERIAL       PRIMARY KEY,
    label                           VARCHAR(100),                    -- 수동 런 식별용(예: "m7-rep1-v2"), 스케줄은 null
    period_start                    TIMESTAMPTZ     NOT NULL,
    period_end                      TIMESTAMPTZ     NOT NULL,
    model_version                   VARCHAR(50),                     -- null = 전 버전 합산
    trigger_type                    VARCHAR(20)     NOT NULL,        -- SCHEDULED | MANUAL
    total_predictions               INT             NOT NULL,
    true_positives                  INT             NOT NULL,
    false_positives                 INT             NOT NULL,
    missed_breaches                 INT             NOT NULL,
    hit_rate                        DOUBLE PRECISION NOT NULL,
    false_positive_rate             DOUBLE PRECISION NOT NULL,
    avg_lead_time_minutes           DOUBLE PRECISION,                -- 적중 없으면 null
    median_lead_time_minutes        DOUBLE PRECISION,
    avg_breach_timing_error_minutes DOUBLE PRECISION,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 스케줄 런만 멱등 보호(부분 유니크): 같은 (기간, 모델버전) SCHEDULED 런은 재기동·중복 발화
-- 시에도 1건. MANUAL 런은 호출자(비교 스크립트)가 의도적으로 다시 뜨는 것이므로 제약하지 않는다.
-- SCHEDULED 경로의 model_version은 항상 non-null(존재하는 버전별 1건)이라 NULL 처리 고민 불필요.
CREATE UNIQUE INDEX uq_evaluation_run_scheduled
    ON evaluation_run (period_start, period_end, model_version)
    WHERE trigger_type = 'SCHEDULED';

CREATE INDEX idx_evaluation_run_created_at ON evaluation_run (created_at DESC);
